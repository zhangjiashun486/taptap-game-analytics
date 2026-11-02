# -*- coding: utf-8 -*-
"""
【文件作用】
    在本地已安装的客户端目录里“文本搜索”接口路径字符串（只读，不修改、不运行客户端）。
    原理：Electron 客户端（如 TapTap PC 版）的界面代码是 .js/.html 文本，
          接口路径以字符串形式写在里面，直接搜索关键字即可找到。

【安全说明】
    只读取本机文件，不联网、不改文件、不绕过任何防护；
    仅用于学习了解客户端调用了哪些公开接口。

【运行方式】
    python scan_client_strings.py                          # 使用默认目录与关键词
    python scan_client_strings.py --pattern webapiv2       # 只搜一个关键词
    python scan_client_strings.py --root "D:/some/app/resources"
"""
import argparse
import json
import os
import re
import sys
from pathlib import Path

# ---------------- 默认配置 ----------------
DEFAULT_ROOT = os.getenv("TAPTAP_CLIENT_ROOT", str(Path.home() / "TapTap" / "resources"))     # 客户端 resources 目录
DEFAULT_PATTERNS = [                                        # 想找的关键词（可增删）
    "webapiv2", "/app/v", "app-top", "detail",
    "search", "pc-game", "review", "hits",
]
DEFAULT_EXTS = (".js", ".html", ".asar")                    # 只搜这几类文件
CONTEXT_CHARS = 80                                          # 命中处前后截取的字符数
MAX_HITS_PER_FILE = 2                                       # 每个文件每个关键词最多记几条

OUT_FILE = Path(__file__).resolve().parents[3] / "data" / "output" / "client_scan_result.json"


def iter_files(root: Path, exts):
    """遍历目录，产出所有指定后缀的文件路径（用 os.walk 递归）"""
    for dirpath, _, filenames in os.walk(root):             # os.walk 逐层遍历目录
        for name in filenames:
            if name.endswith(exts):                         # 只挑 .js/.html/.asar
                yield Path(dirpath) / name


def scan_file(fp: Path, patterns, context=CONTEXT_CHARS, max_hits=MAX_HITS_PER_FILE):
    """在单个文件里搜索所有关键词，返回 [(关键词, 上下文), ...]"""
    hits = []
    try:
        # errors="ignore"：.asar 里可能混有二进制，忽略无法解码的字符，避免报错
        txt = fp.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return hits                                         # 读不了就跳过（不中断整个扫描）
    for pat in patterns:
        count = 0
        for m in re.finditer(re.escape(pat), txt):          # 找到每一次命中及位置
            start = max(0, m.start() - context)             # 向前截取上下文
            end = min(len(txt), m.end() + context)          # 向后截取上下文
            snippet = txt[start:end].replace("\n", " ")     # 换行替换成空格，方便打印
            hits.append((pat, snippet))
            count += 1
            if count >= max_hits:                           # 每个文件每个词最多几条
                break
    return hits


def main():
    parser = argparse.ArgumentParser(description="在客户端目录里搜索接口路径字符串（只读）")
    parser.add_argument("--root", default=DEFAULT_ROOT, help="客户端 resources 目录")
    parser.add_argument("--pattern", action="append", help="只搜索指定关键词（可重复）")
    args = parser.parse_args()

    root = Path(args.root)
    patterns = args.pattern or DEFAULT_PATTERNS
    print(f"搜索目录: {root}")
    print(f"关键词: {patterns}")
    if not root.exists():
        print("目录不存在"); sys.exit(1)

    results = {}                                            # {关键词: [{file, context}, ...]}
    file_count = 0
    for fp in iter_files(root, DEFAULT_EXTS):               # ① 遍历所有目标文件
        file_count += 1
        for pat, snippet in scan_file(fp, patterns):        # ② 在文件里搜索
            results.setdefault(pat, []).append({
                "file": str(fp.relative_to(root)),          # 保留相对路径，输出更短
                "context": snippet[:300],
            })

    print(f"\n共扫描 {file_count} 个文件")
    for pat in patterns:                                    # ③ 打印结果摘要
        lst = results.get(pat, [])
        print(f"\n关键词 '{pat}' → 命中 {len(lst)} 条")
        for item in lst[:5]:                                # 只展示前 5 条
            print(f"  文件: {item['file']}")
            print(f"  上下文: ...{item['context'][:180]}...")

    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)      # 结果保存成 JSON，便于以后查看
    OUT_FILE.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n完整结果已保存: {OUT_FILE}")


if __name__ == "__main__":
    main()