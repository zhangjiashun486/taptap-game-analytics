package org.example.base_server.service.impl;

import org.example.base_server.dao.CrawlTaskDao;
import org.example.base_server.entity.CrawlTask;
import org.example.base_server.entity.CrawlTaskLog;
import org.example.base_server.service.CrawlTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** 采集任务控制：第三阶段执行隔离 Python 演示任务，DEMO 模式保留为回退。 */
@Service
public class CrawlTaskServiceImpl implements CrawlTaskService {

    private static final Logger log = LoggerFactory.getLogger(CrawlTaskServiceImpl.class);
    private static final Set<String> TASK_TYPES = Set.of("game-id", "detail", "archive");

    private final CrawlTaskDao dao;
    private final ExecutorService executor;
    private final String executionMode;
    private final String pythonExecutable;
    private final String scriptPath;
    private final int demoLimit;
    private final long timeoutMinutes;

    public CrawlTaskServiceImpl(
            CrawlTaskDao dao,
            @Qualifier("crawlTaskExecutor") ExecutorService executor,
            @Value("${crawl.execution.mode:PYTHON}") String executionMode,
            @Value("${crawl.python.executable}") String pythonExecutable,
            @Value("${crawl.script.path}") String scriptPath,
            @Value("${crawl.demo.limit:20}") int demoLimit,
            @Value("${crawl.timeout-minutes:10}") long timeoutMinutes) {
        this.dao = dao;
        this.executor = executor;
        this.executionMode = executionMode;
        this.pythonExecutable = pythonExecutable;
        this.scriptPath = scriptPath;
        this.demoLimit = Math.max(1, Math.min(demoLimit, 20));
        this.timeoutMinutes = timeoutMinutes;
    }

    @Override
    public CrawlTask start(String taskType) {
        validateType(taskType);
        CrawlTask running = dao.findRunningByType(taskType);
        if (running != null) {
            return running;
        }

        CrawlTask task = new CrawlTask();
        task.setTaskType(taskType);
        task.setStatus("RUNNING");
        task.setProgress(0);
        task.setStage("初始化任务");
        task.setMessage("任务已创建，准备执行");
        task.setMode("PYTHON".equalsIgnoreCase(executionMode) ? "PYTHON" : "DEMO");
        dao.insertTask(task);
        addLog(task.getId(), "INFO", "任务已创建，执行模式=" + task.getMode());

        executor.submit(() -> runTask(task.getId()));
        return task;
    }

    @Override
    public CrawlTask get(Long taskId) {
        return dao.findById(taskId);
    }

    @Override
    public CrawlTask latest(String taskType) {
        validateType(taskType);
        return dao.findLatestByType(taskType);
    }

    @Override
    public List<CrawlTaskLog> logs(Long taskId, Long afterId) {
        return dao.listLogs(taskId, afterId == null ? 0L : afterId);
    }

    private void runTask(Long taskId) {
        if ("PYTHON".equalsIgnoreCase(executionMode)) {
            runPythonTask(taskId);
        } else {
            runDemo(taskId);
        }
    }
    /** 启动隔离 Python 脚本，实时解析进度、日志和结果。 */
    private void runPythonTask(Long taskId) {
        CrawlTask task = dao.findById(taskId);
        if (task == null) return;

        File executable = new File(pythonExecutable);
        File script = new File(scriptPath);
        if (!executable.exists() || !script.exists()) {
            markFailed(task, "Python 或脚本不存在: executable=" + executable.exists() + ", script=" + script.exists());
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add("-u");
        command.add(scriptPath);
        command.add("--mode");
        command.add(task.getTaskType());
        command.add("--limit");
        command.add(String.valueOf(demoLimit));
        if ("archive".equals(task.getTaskType())) {
            command.add("--hdfs");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(script.getParentFile().getParentFile());
        builder.redirectErrorStream(true);

        int[] result = new int[]{0, 0, 0, 0};
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleProcessLine(task, line, result);
                }
            }

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                markFailed(task, "任务超过 " + timeoutMinutes + " 分钟，已停止");
                return;
            }
            if (process.exitValue() == 0 && result[3] == 0) {
                String message = "隔离演示执行完成：成功 " + result[1] + " / 总计 " + result[0];
                finishTask(task, result[0], result[1], result[2], message);
            } else {
                markFailed(task, "Python 执行失败，exitCode=" + process.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(task, "任务被中断");
        } catch (Exception e) {
            log.error("Python 采集任务执行失败", e);
            markFailed(task, "Python 执行异常: " + e.getMessage());
        }
    }

    private void handleProcessLine(CrawlTask task, String rawLine, int[] result) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) return;

        if (line.startsWith("[PROGRESS]")) {
            String body = line.substring("[PROGRESS]".length()).trim();
            int split = body.indexOf(' ');
            int progress = split > 0 ? Integer.parseInt(body.substring(0, split)) : 0;
            String stage = split > 0 ? body.substring(split + 1).trim() : "执行中";
            task.setStatus("RUNNING");
            task.setProgress(Math.max(0, Math.min(progress, 100)));
            task.setStage(stage);
            task.setMessage("正在执行：" + stage);
            dao.updateTask(task);
            addLog(task.getId(), "INFO", "进度 " + task.getProgress() + "%：" + stage);
            return;
        }

        if (line.startsWith("[RESULT]")) {
            for (String token : line.substring("[RESULT]".length()).trim().split("\\s+")) {
                String[] pair = token.split("=", 2);
                if (pair.length != 2) continue;
                int value;
                try {
                    value = Integer.parseInt(pair[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if ("total".equals(pair[0])) result[0] = value;
                if ("success".equals(pair[0])) result[1] = value;
                if ("failed".equals(pair[0])) result[2] = value;
            }
            return;
        }

        String level = "INFO";
        String message = line;
        if (line.startsWith("[LOG]")) {
            message = line.substring("[LOG]".length()).trim();
        } else if (line.startsWith("[WARN]")) {
            level = "WARN";
            message = line.substring("[WARN]".length()).trim();
        } else if (line.startsWith("[ERROR]")) {
            level = "ERROR";
            result[3] = 1;
            message = line.substring("[ERROR]".length()).trim();
        }
        addLog(task.getId(), level, message);
    }
    /** 回退模式：不调用 Python，只按固定步骤推进数据库状态。 */
    private void runDemo(Long taskId) {
        CrawlTask task = dao.findById(taskId);
        if (task == null) return;

        try {
            for (Step step : stepsFor(task.getTaskType())) {
                Thread.sleep(700);
                task.setStatus("RUNNING");
                task.setProgress(step.progress());
                task.setStage(step.stage());
                task.setMessage(step.message());
                dao.updateTask(task);
                addLog(taskId, "INFO", step.message());
            }
            int[] counts = resultCounts(task.getTaskType());
            finishTask(task, counts[0], counts[1], 0, "DEMO 执行完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(task, "任务被中断");
        } catch (Exception e) {
            log.error("DEMO 采集任务执行失败", e);
            markFailed(task, "任务执行失败: " + e.getMessage());
        }
    }

    private void finishTask(CrawlTask task, int total, int success, int failed, String message) {
        task.setStatus("DONE");
        task.setProgress(100);
        task.setStage("任务完成");
        task.setMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        dao.updateTask(task);
        addLog(task.getId(), "INFO", message);
        insertSummary(task, "DONE", message, total, success, failed);
        log.info("采集任务 {} 执行完成：total={}, success={}, failed={}", task.getId(), total, success, failed);
    }

    private void markFailed(CrawlTask task, String message) {
        if (task == null) return;
        task.setStatus("FAILED");
        task.setStage("执行失败");
        task.setMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        dao.updateTask(task);
        addLog(task.getId(), "ERROR", message);
        int[] counts = resultCounts(task.getTaskType());
        insertSummary(task, "FAILED", message, counts[0], 0, 1);
    }

    private List<Step> stepsFor(String taskType) {
        return switch (taskType) {
            case "game-id" -> List.of(
                    new Step(10, "连接榜单", "正在连接 6 个榜单接口"),
                    new Step(30, "收集种子", "已收集榜单种子，开始扩展标签队列"),
                    new Step(58, "标签闭合", "标签队列扫描中，自动跳过重复游戏 ID"),
                    new Step(82, "本地覆盖写", "正在覆盖写入最新游戏 ID 文件"),
                    new Step(100, "任务收尾", "本地文件更新完成，进入任务记录阶段")
            );
            case "detail" -> List.of(
                    new Step(10, "读取索引", "正在读取游戏 ID 索引与历史进度"),
                    new Step(32, "详情采集", "正在批量请求游戏详情接口"),
                    new Step(58, "字段解析", "正在解析评分、关注、厂商、封面与标签字段"),
                    new Step(82, "JSONL 写入", "正在追加并覆盖最新详情数据文件"),
                    new Step(100, "任务收尾", "详情数据文件更新完成")
            );
            default -> List.of(
                    new Step(10, "数据校验", "正在校验最新 JSONL 数据文件"),
                    new Step(32, "MySQL 入库", "正在执行游戏、标签和关系表覆盖导入"),
                    new Step(60, "数据核对", "正在核对 MySQL 记录数量"),
                    new Step(84, "HDFS 归档", "正在上传到 http://192.168.100.101:50070/taptap"),
                    new Step(100, "任务收尾", "MySQL 与 HDFS 归档流程完成")
            );
        };
    }

    private void insertSummary(CrawlTask task, String status, String message,
                               int total, int success, int failed) {
        dao.insertSummary(
                "TASK-" + task.getId(),
                task.getTaskType(),
                status,
                total,
                success,
                failed,
                task.getStartedAt(),
                task.getFinishedAt(),
                message
        );
    }

    private int[] resultCounts(String taskType) {
        return switch (taskType) {
            case "game-id" -> new int[]{10221, 10221};
            case "detail" -> new int[]{10219, 10219};
            default -> new int[]{10219, 10219};
        };
    }

    private void addLog(Long taskId, String level, String message) {
        CrawlTaskLog item = new CrawlTaskLog();
        item.setTaskId(taskId);
        item.setLevel(level);
        item.setMessage(message);
        dao.insertLog(item);
    }

    private void validateType(String taskType) {
        if (taskType == null || !TASK_TYPES.contains(taskType)) {
            throw new IllegalArgumentException("不支持的任务类型: " + taskType);
        }
    }

    private record Step(int progress, String stage, String message) {
    }
}