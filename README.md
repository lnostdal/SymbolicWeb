# SymbolicWeb

AJAX long poll/Comet/ReverseHTTP/WebSockets/whatever Web UI (WUI) thing written in Clojure.

SW was originally written in Common Lisp, and that version is still found at this location albeit unmaintained:

  https://github.com/lnostdal/old-SymbolicWeb



## Status

Very, very alpha.



## Usage

*This is out of date; if you really want to try this just get in touch instead for now.*


Lighttpd 1.5.x (from svn/git) is used (recommended) for the boring static content. To enable serving from port 80 we do:

    sudo setcap 'cap_net_bind_service=+ep' /usr/local/sbin/lighttpd


..then we start it:

    /usr/local/sbin/lighttpd -f ~/clojure/src/symbolicweb/resources/lighttpd.conf


Start swank in the SW directory:

    ~/clojure/src/symbolicweb$ lein swank


Start Emacs and connect to the now running swank using Slime, then make sure everything's loaded:

    user> (require 'symbolicweb.core)


Now start SW:

    user> (in-ns 'symbolicweb.core)
    symbolicweb.core> (-main)


Direct your browser to http://localhost.nostdal.org/empty-page/sw (yes, this will really resolve to 127.0.0.1). To send JS to the
browser, try:

    symbolicweb.core> (dosync (alert "Hi!"))



## Other things

I'm aiming at a 132 column width using 1920x1080 monitors:
  http://ask.slashdot.org/story/07/07/07/1931246/Are-80-Columns-Enough



### Style

    -this-is-a-global-variable-
    *this-is-a-global-dynamic-variable*

Naming of Container (container_model.clj and container_model_node.clj) related functions follow or match the naming
found in jQuery. E.g., append, prepend, before and after:

* http://api.jquery.com/category/manipulation/dom-insertion-inside/
* http://api.jquery.com/category/manipulation/dom-insertion-outside/



## License

Copyright (C) 2011, 2012 Lars Rune Nøstdal

Distributed under the GNU Affero General Public License (for now):

* http://en.wikipedia.org/wiki/Affero_General_Public_License
* http://www.gnu.org/licenses/agpl.html
