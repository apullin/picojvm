"""Typed failures from pjvmpack stages."""


class PackError(ValueError):
    """Raised when input classes cannot be represented as a .pjvm image."""
