"""Entry point for the full 1405 source and package audit."""
import runpy
from pathlib import Path
runpy.run_path(str(Path(__file__).with_name("validate_all_grade7.py")),run_name="__main__")
