# colorize-irc-logs

Colorize the #clojure at freenode irc logs that Raynes makes
available.

Some colorized #clojure logs currently available at
<http://www.unexpected-vortices.com/clojure/irc-logs/index.html>.

## Usage

Use `lein uberjar`, then:

    $ java -jar colorize-irc-logs-0.1.0-standalone.jar clojure YYYY-MM-DD

It expects a YYYY dir in the current directory, and writes a
MM-DD.html file into it.

## License

Copyright © 2012 John Gabriele <jmg3000@gmail.com>

Distributed under the Eclipse Public License, the same as Clojure.
