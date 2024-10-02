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

```
Usage: blt [<options>]

Options:
  -u, --url=<value>         The URL to traverse
  -s, --stop-after=<int>    The number of recursions to stop crawling after.
                            Default is infinite.
  -i, --ignoreRegex=<text>  The Regex for ignoring
  -l, --list=<text>         path to a file with a list of urls. You still need
                            to specify the base url. currently only 1 base url
                            is allowed even though you might have multiple urls
                            from different domains. I am not planning on fixing
                            that
  -o, --output-file=<text>  The file to save for.
  -L, --log-level=(ERROR|WARN|INFO|DEBUG|TRACE)
                            The log level to log at
  --dont-print-result       Whether to print the result to stdout
  --pretty-print            Whether to pretty print the json output
  -U, --user-agent=<text>   The user agent to use when crawling
  -h, --help                Show this message and exit
```

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

## Credits

- [@alturkovic](https://github.com/alturkovic) who made https://github.com/alturkovic/robots-txt

