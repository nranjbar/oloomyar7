# Grade 7 project rules

- This repository is exclusively `nranjbar/oloomyar7`. All Grade 7 code, commits, builds and review assets belong here.
- The user explicitly forbids further changes to the Grade 9 project. Do not modify `nranjbar/OloomYar789`, its branches, pull requests, workflows or files while working on Grade 7.
- Preserve the copied UI components and interaction behavior. Display the original workbook question before its figures and interactive answer.
- Current scope is chapters 1 and 2 of the Grade 7 workbook, edition 1405, supplied in `haftom.zip` with the separate chapter answer PDFs.
- Keep source question numbering, all subparts, hints, correct alternatives, units and explanations accurate. Do not add invented source questions or source figures.
- Question and answer images live in `app/src/main/assets/images`. Preserve supplied filenames and image content. Chapter 2 question 9 now uses the supplied corrected image: 36 g, 60 and 72 cubic centimetres.
- Practice requires two distinct complete incorrect submissions and both displayed hints before the reference-answer button becomes active. Automatically show hint 1 after the first failure and hint 2 after the second. A correct answer may continue immediately. Do not let unchanged answers, old drafts, restarting a question or exam-result review bypass the rule.
- Rewrite hints for each individual learning step without giving its final number, option letter or completed answer list. Show the full answer image only after all parts of the question have been answered correctly or unlocked under the attempt policy.
- Figure numbers are authoritative: chapter 2 question 12 uses 1=watch and 2=stopwatch; question 15 uses 1=balance, 2=graduated cylinder and 3=the group of force meters. Keep question 14's two pencil diagrams numbered 1 and 2.
- Keep `review/` assets and answer-image recommendations synchronized with app content. Avoid linking a missing answer-image file.
- Validate content with `python3 scripts/validate_grade7.py`. Use the repository's Grade 7 Review workflow for Android builds and native UI checks when application code changes.
