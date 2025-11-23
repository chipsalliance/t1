#!/usr/bin/env python3

import os
import argparse
from pathlib import Path
from typing import List, Optional, TextIO, TypedDict

YAML_TAB = "  "


class BlockFormatError(Exception):
    """Custom exception for block formatting errors."""

    pass


class DocComment(TypedDict):
    file: str
    line: int
    frontmatter: list[str]
    body: list[str]


def doc_comment_to_yaml(dc: DocComment, writer: TextIO):
    list_marker = "- "

    def indent_write(lvl: int, content: str):
        if lvl > 0:
            writer.write((YAML_TAB * lvl) + content)
        else:
            writer.write(content)

    indent_write(1, list_marker + f'file: "{dc['file']}"\n')
    indent_write(2, f'line: {dc["line"]}\n')
    for line in dc["frontmatter"]:
        indent_write(2, line)

    indent_write(2, "body: |\n")

    body: str = ""
    for text in dc["body"]:
        if text == "\n":
            body += text
        else:
            body += (YAML_TAB * 3) + text
    writer.write(body)

    writer.write("\n")


def find_files(directory: str, extension: str) -> List[Path]:
    """
    Search given directory recursively for files with specific extension.
    """
    if not directory.endswith("/"):
        directory = directory + "/"

    search_path = Path(directory)

    if not extension.startswith("."):
        extension = "." + extension

    # rglob('*' + extension) performs the recursive search
    return list(search_path.rglob(f"*{extension}"))


def parse_block_content(
    block_lines: List[str], line_offset: int, file_path: Path
) -> DocComment:
    """
    Handles the logic for parsing a collected block of lines.
    """
    cleaned_lines: list[str] = []

    for line in block_lines:
        stripped = line.lstrip()
        if stripped.startswith("///"):
            content = stripped[3:]
            if content.startswith(" "):
                content = content[1:]
            cleaned_lines.append(content)

    if len(cleaned_lines) == 0:
        raise BlockFormatError(
            f"Error in {file_path} at line {line_offset}: "
            "Block contains no doc comment"
        )

    if not cleaned_lines[0].strip() == "---":
        raise BlockFormatError(
            f"Error in {file_path} at line {line_offset}: "
            "Block does not start with triple hyphens ('---')."
        )

    # Find the closing hyphen
    closing_index = -1
    for i, li in enumerate(cleaned_lines):
        if li.strip() == "---":
            closing_index = i
    if closing_index == -1:
        raise BlockFormatError(
            f"Error in {file_path} at line {line_offset}: "
            "Block starts with '---' but has no closing '---'."
        )

    # Collect frontmatter
    header_lines = cleaned_lines[1:closing_index]

    # Collect body
    body_lines = cleaned_lines[closing_index + 1 :]

    return {
        "file": str(file_path),
        "line": line_offset,
        "frontmatter": header_lines,
        "body": body_lines,
    }


def build_yml_block(file_path: Path, writer: TextIO) -> Optional[str]:
    """
    Scan the document, find lines starting with ///.
    Collect those lines as a block.
    """
    lines = []
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            lines = f.readlines()
    except UnicodeDecodeError:
        print(f"Skipping binary or non-utf8 file: {file_path}")
        return None

    current_block = []
    start_line_num = 0
    in_block = False
    all_blocks: list[DocComment] = []

    for i, line in enumerate(lines):
        is_slash_line = line.lstrip().startswith("///")

        if is_slash_line:
            if not in_block:
                # Start of a new block
                in_block = True
                start_line_num = i + 1
            current_block.append(line)
        else:
            if in_block:
                # End of a block detected
                try:
                    result = parse_block_content(
                        current_block, start_line_num, file_path
                    )
                    all_blocks.append(result)
                except BlockFormatError as e:
                    print(f"[PARSING ERROR] {e}")

                # Reset
                current_block = []
                in_block = False

    # Handle case where file ends while inside a block
    if in_block:
        try:
            result = parse_block_content(current_block, start_line_num, file_path)
            all_blocks.append(result)
        except BlockFormatError as e:
            print(f"xx [PARSING ERROR] {e}")

    for blk in all_blocks:
        doc_comment_to_yaml(blk, writer)


def main():
    parser = argparse.ArgumentParser(description="Scan for doc comment.")
    parser.add_argument("directory", help="The root directory to search")
    parser.add_argument("extension", help="File extension to filter (e.g., .asl)")
    parser.add_argument("output", help="File to save all metadata")

    args = parser.parse_args()

    root_dir = args.directory
    ext = args.extension
    output: str = args.output
    if not output.endswith(".yml"):
        output = output + ".yml"

    if not os.path.isdir(root_dir):
        print(f"Error: Directory '{root_dir}' not found.")
        return

    print(f"Scanning '{root_dir}' for extension '{ext}'...\n")

    files = find_files(root_dir, ext)

    if not files:
        print(f"No files suffix with {ext} found.")
        return

    with open(output, "w") as out:
        out.write("doc-comments: \n")
        for file_path in files:
            build_yml_block(file_path, out)


if __name__ == "__main__":
    main()
