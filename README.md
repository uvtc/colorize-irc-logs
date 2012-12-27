# colorize-irc-logs

Colorize the #clojure (and other) freenode irc logs that Raynes
generously makes available.


## Prerequisites

Have Java and lein installed.


## Usage

Grab the colorize-irc-logs source, then build & install:

    cd colorize-irc-logs
    lein uberjar
    cp target/colorize-irc-logs-0.1.0-standalone.jar ~/bin

And now have a look at some colorized logs:

```bash
cd ~/temp
mkdir 2012  # The program expects a "YYYY" dir in the current directory.
java -jar ~/bin/colorize-irc-logs-0.1.0-standalone.jar clojure 2012-MM-DD
ls -l 2012
```

(replace *MM-DD* with a month and day).



## License

Copyright © 2012 John Gabriele <jmg3000@gmail.com>

Distributed under the Eclipse Public License, the same as Clojure.
