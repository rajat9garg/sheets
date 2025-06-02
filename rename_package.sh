#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: $0 <new_name>"
  exit 1
fi

OLD="sheets"
NEW="$1"

# 1. Replace all occurrences in file contents (case-sensitive)
grep -rl --exclude-dir=.git "$OLD" . | xargs sed -i "" "s/$OLD/$NEW/g"

# 2. Rename directories and files containing the old name
find . -depth -name "*$OLD*" | while read fname; do
  newfname=$(echo "$fname" | sed "s/$OLD/$NEW/g")
  mv "$fname" "$newfname"
done

echo "All occurrences of '$OLD' have been replaced with '$NEW'."
