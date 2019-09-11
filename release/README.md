Fresh Fiji installation:
------------------------
1) Download recent version of Fiji from http://fiji.sc/
2) Install it, and pay attention where (which Folder) it got installed into
3) Locate the folder where Fiji got installed, say it is folder *FIJIROOT*
4) Download both [DAIS-wp13-1.0.1.jar](https://raw.githubusercontent.com/xulman/DAIS-wp1.3/master/release/DAIS-wp13-1.0.1.jar) and [jeromq-0.5.1.jar](https://maven.scijava.org/service/local/repositories/central/content/org/zeromq/jeromq/0.5.1/jeromq-0.5.1.jar) files, and place them into *FIJIROOT*/plugins


Upgrading existing Fiji installation:
-------------------------------------
Proceed only with steps 3 and 4.


Usage:
------
The plugin installs 4 commands, each with its own GUI dialog.
The commands are available from the menu:

- File->Import->Receive One Image
- File->Import->Receive Multiple Images
- File->Export->Send Current Image
- File->Export->Send All Opened Images

Sometimes the 'Class not found' exception is fired always after running the
command (after hitting OK button in the dialogs). This has been observed
with versions of Fiji that come with older version of jeromq-0.A.B.jar in
*FIJIROOT*/jars where A < 5. After installing the above jeromq-0.5.1.jar
into your Fiji, multiple versions of the same library will exist and the
wrong/old one might be picked up. Since this library/plugin does not work
with the older one, the only workaround is to remove the old version, which
might unfortunately brake something network-related else in the Fiji.


11th Sep, 2019
Vladimir Ulman

https://github.com/xulman/DAIS-wp1.3
