# The chosen language is persisted by enum name and read back by comparing that name, and
# the protocol store on disk carries enum names in its JSON, so the constants have to keep
# the spelling they were written with. Without this a minified build reopens in the wrong
# language and cannot read its own protocols back, and nothing warns about any of it.
-keepclassmembers enum dev.peringer.limen.** { *; }
