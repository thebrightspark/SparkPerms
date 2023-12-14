# SparkPerms

A simple Fabric permissions mod which allows OPs to manage a list of command permissions of which all players can use.

This uses [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api).

All permissions configured are saved to a text file in `<instance>/config/sparkperms.txt`.

The following commands are available in-game:

- `/perms list`  
  Lists all of the configured permissions.
- `/perms reload`  
  Reloads the configured permissions from the config file (useful if it has been externally modified).
- `/perms allow <permission>`  
  Allows the given permission for all players.
- `/perms revoke <permission> [recursive:true|false]`
  Revokes the given permission for all players.
  If the optional `recursive` argument is provided as `true`, then all permissions starting with the one given will be
  revoked.
- `/perms clear`  
  Revokes ALL permissions.
