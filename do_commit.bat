@echo off
cd /d C:\Users\Admin\IdeaProjects\OpenSoundboard
del fix_bom.py 2>nul
git add -A
git commit -m "v0.2.1 - Remove context menu, fix all-version compile errors" -m "- Removed right-click rename/delete context menu from all versions" -m "- Fixed BOM and API errors in v1_21_1, v1_21_4, v1_21_5, v1_21_8, v1_21_10" -m "- All 6 supported versions now build cleanly"
git push

