#!/bin/bash

set -e

no_test=0
no_lint=0

while (( $# > 0 )); do
   case "$1" in
   	--help)
			printf "run.sh [OPTION]... [DIR]\n"
			printf "options:\n"
			printf "\t--help			Show help\n"
			printf "\t--no-test		Skip tests\n"
			printf "\t--no-lint		Skip linting\n"
			exit 0
      	;;
      --no-test)
			no_test=1
			shift
      	;;
      --no-lint)
			no_lint=1
			shift
			;;
		*)
			break
	      ;;
   esac
done

./mill __.compile

if (( no_test == 0 )); then
  if [[ -z "$1" ]]; then
    ./mill __.test
  elif ./mill resolve modules["$1"].__.test &>/dev/null; then
    ./mill modules["$1"].__.test
  else
    red='\033[0;31m'
    no_color='\033[0m'
	  printf "%bNo tests found in: %s%b\n" "$red" "$1" "$no_color"
  fi
fi

if (( no_lint == 0 )); then
	if [[ -z "${CI}" ]]; then
	  ./mill modules[_].__.fix
	  ./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll modules[_].__.sources
	else
		./mill modules[_].__.fix --check
		./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll modules[_].__.sources
	fi
fi
