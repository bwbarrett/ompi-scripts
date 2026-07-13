#!/usr/bin/env python3

'''Generate a spreadsheet of who has write access to what in the
open-mpi GitHub organization, for use in the annual committer review.

Every non-archived repo in the org becomes a column; every human who is
on at least one team with more than "pull" (read-only) access to at
least one of those repos becomes a row.  The cell where they intersect
lists the team(s) that grant that person access to that repo, and is
color-coded by the highest permission level those teams confer.

Requirements:

1. The "gh" CLI, authenticated as someone with admin rights in the
   open-mpi org (teams and their members are not public):

   $ brew install gh      # or your platform's equivalent
   $ gh auth login

   Verify with "gh auth status".  Note that "gh" needs the "read:org"
   scope; if you get 403s, run:

   $ gh auth refresh -h github.com -s read:org

2. The openpyxl python module, to write the .xlsx file:

   $ pip3 install openpyxl

'''

import json
import argparse
import subprocess

from datetime import datetime
from concurrent.futures import ThreadPoolExecutor

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter

#--------------------------------------------------------------------

default_org = 'open-mpi'
default_outfile = 'permissions.xlsx'

# GitHub permission levels, in increasing order of power.  We ignore
# "pull" (i.e., read-only) access: this review is about who can *write*
# to our repos.
permission_levels = ['pull', 'triage', 'push', 'maintain', 'admin']

# How each permission level is displayed in the spreadsheet: a
# human-readable name, a cell fill, and a font color.
permission_styles = {
    'admin' : {
        'label' : 'Admin',
        'fill'  : 'FFF8D7DA',
        'font'  : 'FF842029',
    },
    'maintain' : {
        'label' : 'Maintain',
        'fill'  : 'FFFFE5D0',
        'font'  : 'FF8A4B08',
    },
    'push' : {
        'label' : 'Write',
        'fill'  : 'FFD1E7DD',
        'font'  : 'FF0F5132',
    },
    'triage' : {
        'label' : 'Triage',
        'fill'  : 'FFFFF3CD',
        'font'  : 'FF664D03',
    },
}

# Colors used for the non-permission parts of the sheet
title_font_color  = 'FFFFFFFF'
title_fill        = 'FF1F3864'
header_fill       = 'FF2F5597'
identity_fill     = 'FFEFF3FA'
band_fill         = 'FFF7F9FC'
grid_color        = 'FFD0D7E5'

#--------------------------------------------------------------------

def run_gh(endpoint, paginate=True):
    '''Invoke "gh api" on a REST endpoint and return the parsed JSON.

    With --paginate and --slurp, "gh" returns a JSON array of the
    per-page arrays' contents already flattened into a single array.
    '''
    cmd = ['gh', 'api']
    if paginate:
        cmd.extend(['--paginate', '--slurp'])
    cmd.append(endpoint)

    try:
        out = subprocess.run(cmd, check=True, capture_output=True, text=True)
    except FileNotFoundError:
        print('ERROR: Cannot find the "gh" command -- is it installed?')
        exit(1)
    except subprocess.CalledProcessError as e:
        print(f'ERROR: "gh api {endpoint}" failed:')
        print(e.stderr.strip())
        exit(1)

    data = json.loads(out.stdout)

    # --slurp gives us a list of pages; flatten it back down to the
    # list of items that the caller is expecting.
    if paginate:
        flat = list()
        for page in data:
            flat.extend(page)
        return flat

    return data

def highest_permission(permissions):
    '''Return the most powerful permission in a list of permissions.'''
    return max(permissions, key=permission_levels.index)

#--------------------------------------------------------------------

def get_repos(org):
    '''Return the org's non-archived repos, sorted by name.

    Archived repos are read-only, so no one can write to them,
    regardless of what the teams say.  They are just noise in the
    annual review process.
    '''
    print(f'Loading repos in the "{org}" organization...')
    repos = run_gh(f'orgs/{org}/repos')

    active = list()
    for repo in repos:
        if repo['archived']:
            print(f'  Found repo: {repo["name"]} -- SKIPPED (archived)')
            continue

        print(f'  Found repo: {repo["name"]}')
        active.append(repo)

    return sorted(active, key=lambda r: r['name'].lower())

def get_repo_teams(org, repo):
    '''Return the teams with more than read-only access to a repo.'''
    teams = run_gh(f'repos/{org}/{repo["name"]}/teams')

    writers = list()
    for team in teams:
        name       = team['name']
        permission = team['permission']

        out = f'  {repo["name"]}: team {name} ({permission})'
        if permission not in permission_styles:
            print(f'{out} -- SKIPPED (read-only)')
            continue

        print(out)
        writers.append(team)

    return writers

def get_team_members(org, slug, name):
    '''Return the logins of the members of a team.'''
    members = run_gh(f'orgs/{org}/teams/{slug}/members')
    logins = sorted([m['login'] for m in members], key=str.lower)

    print(f'  Team {name}: {len(logins)} members')

    return logins

def get_user(login):
    '''Return the public profile of a single user.'''
    return run_gh(f'users/{login}', paginate=False)

#--------------------------------------------------------------------

def gather(org):
    '''Collect everything we need from GitHub.

    Returns the list of repos and a dict of login -> user info, where
    each user's 'repos' is a dict of repo name -> the teams (and the
    resulting permission) that give them access to that repo.
    '''
    repos = get_repos(org)

    # Find the writable teams on each repo.  These are independent
    # queries, so run a handful of them at a time -- the org has enough
    # repos that doing this serially is noticeably slow.
    print('\nLoading teams on each repo...')
    with ThreadPoolExecutor(max_workers=8) as pool:
        repo_teams = pool.map(lambda r: get_repo_teams(org, r), repos)

    # A given team is frequently on more than one repo; only look up
    # its membership once.  Note that a team's *membership* is a
    # property of the team, but its *permission* is a property of the
    # (team, repo) pair -- the same team can have different permissions
    # on different repos.  So keep the two separate: members are looked
    # up once per team here, and the permission stays on the per-repo
    # copy of the team that the repo query gave us.
    teams = dict()
    for repo, team_list in zip(repos, repo_teams):
        repo['teams'] = team_list
        for team in team_list:
            teams.setdefault(team['slug'], team['name'])

    print(f'\nLoading members of {len(teams)} teams...')
    slugs = sorted(teams.keys())
    with ThreadPoolExecutor(max_workers=8) as pool:
        members = pool.map(lambda s: get_team_members(org, s, teams[s]), slugs)

    team_members = dict(zip(slugs, members))

    # Invert the above: for each human, which repos can they write to,
    # and via which teams?
    users = dict()
    for repo in repos:
        for team in repo['teams']:
            for login in team_members[team['slug']]:
                if login not in users:
                    users[login] = { 'repos' : dict() }

                entry = users[login]['repos'].setdefault(repo['name'], {
                    'teams'       : list(),
                    'permissions' : list(),
                })
                entry['teams'].append(team['name'])
                entry['permissions'].append(team['permission'])

    # Fill in the name / email / company of each human.  These are
    # independent queries, too.
    print(f'\nLoading profiles of {len(users)} people...')
    logins = sorted(users.keys(), key=str.lower)
    with ThreadPoolExecutor(max_workers=8) as pool:
        profiles = pool.map(get_user, logins)

    for login, profile in zip(logins, profiles):
        users[login]['profile'] = profile
        print(f'  {login}: {profile.get("name")}')

    return repos, users

#--------------------------------------------------------------------

def write_xlsx(org, repos, users, outfile):
    '''Write the spreadsheet.'''

    thin   = Side(style='thin', color=grid_color)
    border = Border(left=thin, right=thin, top=thin, bottom=thin)

    wb = Workbook()
    ws = wb.active
    ws.title = 'Write access'

    identity_columns = ['Login', 'Name', 'Email', 'Company', '# repos']
    columns = identity_columns + [repo['name'] for repo in repos]
    last_column = get_column_letter(len(columns))

    # Row 1: the title, spanning the whole sheet
    when = datetime.now().strftime('%d %b %Y')
    ws['A1'] = f'{org}: GitHub write access as of {when}'
    ws['A1'].font = Font(bold=True, size=16, color=title_font_color)
    ws['A1'].fill = PatternFill('solid', fgColor=title_fill)
    ws['A1'].alignment = Alignment(horizontal='left', vertical='center')
    ws.merge_cells(f'A1:{last_column}1')
    ws.row_dimensions[1].height = 30

    # Row 2: the legend, so that a reader can decode the cell colors
    # without having to go looking for this script
    ws['A2'] = ('Cells show the team(s) granting access, colored by '
                'permission:')
    ws['A2'].font = Font(italic=True)
    ws['A2'].alignment = Alignment(horizontal='right', vertical='center')
    ws.merge_cells('A2:D2')

    column = len(identity_columns)
    for permission in reversed(permission_levels):
        if permission not in permission_styles:
            continue

        style = permission_styles[permission]
        column += 1
        cell = ws.cell(row=2, column=column, value=style['label'])
        cell.font = Font(bold=True, color=style['font'])
        cell.fill = PatternFill('solid', fgColor=style['fill'])
        cell.alignment = Alignment(horizontal='center', vertical='center')
        cell.border = border

    ws.row_dimensions[2].height = 20

    # Row 3: the column headings.  The repo names are long and the
    # columns are narrow, so stand them on end.
    for column, heading in enumerate(columns, start=1):
        cell = ws.cell(row=3, column=column, value=heading)
        cell.font = Font(bold=True, color=title_font_color)
        cell.fill = PatternFill('solid', fgColor=header_fill)
        cell.border = border

        if column <= len(identity_columns):
            cell.alignment = Alignment(horizontal='left', vertical='bottom')
        else:
            cell.alignment = Alignment(horizontal='center',
                                       vertical='bottom',
                                       textRotation=90)

    ws.row_dimensions[3].height = 120

    # Rows 4+: one row per human, sorted by login
    row = 3
    for login in sorted(users.keys(), key=str.lower):
        row += 1
        user = users[login]
        profile = user['profile']

        # Band every other row: this sheet is far too wide to read
        # across without a little help.
        banded = PatternFill('solid', fgColor=band_fill) if row % 2 == 0 else None

        values = [
            login,
            profile.get('name'),
            profile.get('email'),
            profile.get('company'),
            len(user['repos']),
        ]
        for column, value in enumerate(values, start=1):
            cell = ws.cell(row=row, column=column, value=value)
            cell.border = border
            cell.alignment = Alignment(horizontal='left', vertical='center')
            cell.fill = PatternFill('solid', fgColor=identity_fill)

        # The login is the row's identity; make it stand out
        ws.cell(row=row, column=1).font = Font(bold=True)
        ws.cell(row=row, column=5).alignment = Alignment(horizontal='center',
                                                         vertical='center')

        for column, repo in enumerate(repos, start=len(identity_columns) + 1):
            cell = ws.cell(row=row, column=column)
            cell.border = border
            cell.alignment = Alignment(horizontal='center', vertical='center',
                                       wrap_text=True)

            entry = user['repos'].get(repo['name'])
            if not entry:
                if banded:
                    cell.fill = banded
                continue

            permission = highest_permission(entry['permissions'])
            style = permission_styles[permission]

            cell.value = ', '.join(sorted(entry['teams'], key=str.lower))
            cell.font = Font(bold=True, color=style['font'])
            cell.fill = PatternFill('solid', fgColor=style['fill'])

    # Make it navigable: freeze the headings and the identity columns,
    # and let the reader sort/filter by any column.
    ws.freeze_panes = ws.cell(row=4, column=len(identity_columns) + 1)
    ws.auto_filter.ref = f'A3:{last_column}{row}'

    # Widths: the identity columns hold text, the repo columns hold
    # team names that we wrap, so they can all be the same narrow width.
    for column, width in enumerate([16, 24, 30, 26, 8], start=1):
        ws.column_dimensions[get_column_letter(column)].width = width
    for column in range(len(identity_columns) + 1, len(columns) + 1):
        ws.column_dimensions[get_column_letter(column)].width = 18

    print(f'\nWriting: {outfile}')
    wb.save(outfile)

#--------------------------------------------------------------------

parser = argparse.ArgumentParser(description=
                                 'Generate the annual GitHub committer '
                                 'review spreadsheet')
parser.add_argument('--org', default=default_org,
                    help=f'GitHub organization (default: "{default_org}")')
parser.add_argument('--out', default=default_outfile,
                    help=f'Output .xlsx file (default: "{default_outfile}")')

args = parser.parse_args()

repos, users = gather(args.org)
if not users:
    print(f'ERROR: Found no one with write access in "{args.org}" (!)')
    print('Are you authenticated to "gh" as an org admin?')
    exit(1)

write_xlsx(args.org, repos, users, args.out)

print(f'{len(users)} people with write access across {len(repos)} repos')
