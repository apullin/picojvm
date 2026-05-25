#!/usr/bin/env python3
"""Compatibility shim for the pjvmpack package."""

import sys

from pjvmpack.cli import main


if __name__ == "__main__":
    sys.exit(main())
