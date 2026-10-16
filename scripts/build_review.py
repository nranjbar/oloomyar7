"""Compatibility entry point for the current three-chapter author review."""
from build_source_review import web_review, pdf_review

if __name__ == '__main__':
    web_review()
    pdf_review()
