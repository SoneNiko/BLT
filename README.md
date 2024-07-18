# Broken Link Traverser

## Overview

Broken Link Traverser is a command-line tool designed to crawl a specified base URL, recursively check all the links
found on the pages, and report any broken or inaccessible links. This tool can help you maintain the integrity of your
web resources by identifying broken links.

## Features

- Traverse links from a specified base URL.
- Recursively check links up to a specified recursion depth.
- Ignore links matching a specific regex pattern.
- Load additional URLs from a file.
- Save results to a file.
- Pretty print JSON output.
- Option to suppress printing results to the console.

## Usage

### Command Line Options

- `-u`, `--url` (required): The base URL to start traversing from.
- `-s`, `--stop-after`: The number of recursion levels to stop crawling after (default is infinite).
- `-i`, `--ignoreRegex`: A regex pattern to ignore specific links.
- `-l`, `--list`: Path to a file containing additional URLs to check.
- `-o`, `--output-file`: The file to save the results to.
- `-L`, `--log-level`: The log level to use (default is INFO).
- `--dont-print-result`: Suppress printing the result to stdout.
- `--pretty-print`: Pretty print the JSON output.

### Example

```sh
blt -u https://example.com -s 3 -i "ignore-this-pattern" -l urls.txt -o results.json --pretty-print
```

### Sample Output

```json
[
  {
    "parent": null,
    "url": "https://example.com",
    "status": "200 OK"
  },
  {
    "parent": "https://example.com",
    "url": "https://example.com/broken-link",
    "errorMsg": "[IOException]: Connection refused"
  }
]
```

## Installation

1. Download the latest version of the BLT from the releases page.
2. Run the tool using the command line as shown in the usage section.

## Notes

- This tool ignores `robots.txt` files. Use it responsibly and ensure you have permission to crawl the specified URLs.
- The tool currently supports only one base URL even if multiple URLs from different domains are provided in the list
  file.

## License

This project is licensed under the MIT License.

---

> [!CAUTION]
> This tool ignores `robots.txt` files. User discretion is advised. There will be no liability from the author if the
> wrongful usage of this CLI tool on infrastructure, and links on that infrastructure that you don't have the permission
> to crawl, causes harm, a lawsuit, or an international incident. Use responsibly.
