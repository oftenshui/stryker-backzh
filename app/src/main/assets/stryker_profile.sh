# Stryker chroot shell environment.
#
# Deployed into the chroot at /etc/stryker_profile.sh and sourced from
# /etc/profile, so every interactive login shell the terminal opens (it enters
# via `ash -l`) gets a cool, root-aware, blue two-line prompt instead of the
# bland default. Also adds a few quality-of-life aliases.
#
# Note: \h is intentionally NOT used — the chroot shares the device UTS
# namespace, so \h is the Android hostname ("localhost"), which is exactly the
# dull prompt this replaces. We brand the prompt "⚡Stryker" instead.

# Only dress up interactive shells.
case "$-" in
    *i*) ;;
    *) return 0 2>/dev/null || exit 0 ;;
esac

# Blue palette, wrapped in \[ \] so the line editor keeps the cursor maths right.
__s_rail='\[\033[1;38;5;33m\]'   # bold blue — rails
__s_path='\[\033[38;5;75m\]'     # light blue — path
__s_dim='\[\033[2m\]'
__s_rst='\[\033[0m\]'

# Root → brighter blue brand; normal user → blue. \$ renders '#' for root, '$' otherwise.
if [ "$(id -u 2>/dev/null)" = "0" ]; then
    __s_brand='\[\033[1;38;5;39m\]'
else
    __s_brand='\[\033[1;38;5;33m\]'
fi
__s_tip="${__s_brand}"'\$'"${__s_rst}"

#   ┌──(⚡Stryker)─[ ~/current/path ]
#   └─#
PS1="${__s_rail}┌──${__s_rst}${__s_dim}(${__s_rst}${__s_brand}⚡Stryker${__s_rst}${__s_dim})${__s_rst}${__s_dim}─[${__s_rst}${__s_path}\\w${__s_rst}${__s_dim}]${__s_rst}\n${__s_rail}└─${__s_rst}${__s_tip} "
PS2="${__s_rail}└─${__s_rst}${__s_dim}>${__s_rst} "
export PS1 PS2
[ -z "$TERM" ] && export TERM=xterm-256color

# Quality-of-life aliases.
alias ls='ls --color=auto 2>/dev/null || ls'
alias ll='ls -lh'
alias la='ls -lha'
alias l='ls -CF'
alias ..='cd ..'
alias ...='cd ../..'
alias grep='grep --color=auto'
alias egrep='egrep --color=auto'
alias ports='netstat -tulpn 2>/dev/null || ss -tulpn'

# One tasteful banner per interactive login shell.
if [ -z "$__STRYKER_GREETED" ]; then
    __STRYKER_GREETED=1
    printf '\033[1;38;5;39m⚡ STRYKER\033[0m \033[2m- pentest shell\033[0m\n'
fi

unset __s_rail __s_path __s_dim __s_rst __s_brand __s_tip
