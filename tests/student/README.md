# Own tests

- `edge_cases.json` — 32 boundary fixtures (input + expected output) for all eight commands.
- `stress_test.py` — runs the fixtures, 3200 random small cases against independent Python
  brute-force oracles (all in one process, to catch stale state) and 9 maximum-size scale cases.
  Run from the repository root after `python tools/run.py --setup`:
  `python tests/student/stress_test.py [--seed N] [--count N]`.
- `SearchBench.java` — reproducible naive/KMP/RK timing comparison (see ANALYSIS.md):
  `javac --release 17 -cp Java/build -d Java/build tests/student/SearchBench.java && java -Xmx384m -cp Java/build SearchBench 42`.
- `bench_output.txt` — raw output of the benchmark run used in ANALYSIS.md.

ANALYSIS.md explains what defect each case detects.
