#!/usr/bin/env python3
"""
简化版热词 tokenize 脚本
专门用于 cjkchar+bpe 模型的中文热词
"""
import sys

def is_chinese_char(char):
    """判断是否为中文字符"""
    return '\u4e00' <= char <= '\u9fff'

def tokenize_cjkchar_bpe(text):
    """
    对 cjkchar+bpe 模型进行 tokenization
    中文：每个字一个 token
    其他：保持原样（简化处理）
    """
    tokens = []
    for char in text:
        if is_chinese_char(char):
            tokens.append(char)
        elif char.strip():  # 非空白字符
            tokens.append(char)
    return tokens

def process_hotwords(input_file, output_file):
    """处理热词文件"""
    with open(input_file, 'r', encoding='utf-8') as f_in, \
         open(output_file, 'w', encoding='utf-8') as f_out:

        for line in f_in:
            line = line.strip()
            if not line:
                continue

            # 分离热词和权重 (格式: "津安达 :20.0")
            parts = line.split()
            hotword = []
            metadata = []  # 权重等信息

            for part in parts:
                if part.startswith(':') or part.startswith('#') or part.startswith('@'):
                    metadata.append(part)
                else:
                    hotword.append(part)

            # tokenize 热词
            hotword_text = ''.join(hotword)
            tokens = tokenize_cjkchar_bpe(hotword_text)

            # 输出：tokens + metadata
            output = ' '.join(tokens)
            if metadata:
                output += ' ' + ' '.join(metadata)

            f_out.write(output + '\n')
            print(f"原文: {hotword_text}")
            print(f"Token: {output}")
            print()

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("用法: python3 tokenize_hotwords.py input.txt output.txt")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]

    print("=" * 50)
    print("热词 Tokenization (cjkchar+bpe)")
    print("=" * 50)
    print()

    process_hotwords(input_file, output_file)

    print("=" * 50)
    print(f"✓ 完成！输出文件: {output_file}")
    print("=" * 50)
