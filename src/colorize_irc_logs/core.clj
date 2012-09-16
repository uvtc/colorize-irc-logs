(ns colorize-irc-logs.core
  (:require [clojure.string :as str]
            [hiccup.util :as hu])
  (:gen-class))

;; ----------------------------------------------------------------------
;; TODO:
;;
;;   * break the line if someone accidentally leans on their keyboard
;;     creating a disasterously-long line which the browser won't
;;     wrap.
;;
;;   * think about adding logic such that "foo", "foo`", "foo``"
;;     "foo_", and "foo__" all get the same color.


;; ----------------------------------------------------------------------
;; Would be nice if we could compose regexen, instead of repeating
;; this everytwhere: Regex for nicks that's copy/pasted everywhere in
;; this file: #"[\w_|^`\\-]+"


;; ----------------------------------------------------------------------
;; Send a pull-request to set a custom color of your own. If you
;; choose a named color listed below in `all-other-colors` and add it
;; here, please also remove it from `all-other-colors`. Feel free to
;; put in two (one for "foo" and one for "foo`" or "foo_") if you feel
;; compelled to.
(def custom-user-colors {"clojurebot"  "#375A99"
                         "lazybot"     "#2D8C57"
                         })

;; First user on for the day gets HotPink!
(def all-other-colors ["HotPink"
                       "RoyalBlue"
                       "DarkCyan"
                       "BlueViolet"
                       "Brown"
                       "Crimson"
                       "DarkBlue"
                       "DarkGreen"
                       "DarkGoldenRod"
                       "DarkMagenta"
                       "CornflowerBlue"
                       "DarkRed"
                       "DarkSalmon"
                       "DarkSlateBlue"
                       "ForestGreen"
                       "DarkOrchid"
                       "GoldenRod"
                       "LightCoral"
                       "IndianRed"
                       "Indigo"
                       "Chocolate"
                       "MediumSlateBlue"
                       "LightSeaGreen"
                       "MediumVioletRed"
                       "Olive"
                       "Orange"
                       "SaddleBrown"
                       "Orchid"
                       "Purple"
                       "SteelBlue"
                       "Tomato"])

(def base-url "http://www.raynes.me/logs/irc.freenode.net/")

(def html-header "<!doctype html>
<html>
<head>
<title>irc log: {{title}}</title>
<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />
<style type=\"text/css\">
body {background-color: #f8f8f8;}
table {
    border: 1px solid #ddd;
    border-collapse: collapse;
    width: 100%;
}
th, tr, td {
    border: 1px solid #ddd;
    padding: 2px;
}
td {
    padding: 2px 4px 2px 4px;
}
th {
    background-color: #eee;
}
.timestamp { font-size: small; font-family: monospace; color: #888; }
.timestamp a:link { color: #888; text-decoration: none; }
.timestamp a:hover { text-decoration: underline; }
.comment { font-style: italic; }
.clojurebot, .lazybot { font-family: monospace; }
.lazybot    { background-color: #D5F7E4; }
.clojurebot { background-color: #DEE8FA; }
</style>
<body>
<h1>IRC Log for {{title}}</h1>
<p><a href=\"../index.html\">Back to master index.</a></p>
<p>(Non-custom colors used here, in this order: {{colors}}).</p>
<br/>
<table>
<tr><th>Time</th><th>Nick</th><th>Comment</th></tr>
")

(def html-footer "
</table>
</body>
</html>
")

(declare render-log-as-html)
(declare parse-out-all-users)
(declare assign-colors-to-users)

(defn -main
  "Expects 2 args: the channel name and the date in YYYY-MM-DD format."
  [& args]
  (let [channel     (first args)
        date        (second args)
        infilename  (str date ".txt")
        outfilename (str (subs date 0 4)
                         "/"
                         (subs (str date ".html") 5)) ;; "YYYY/MM-DD.html".
        url         (str base-url channel "/" infilename)
        content     (if (.exists (java.io.File. infilename))
                      (slurp infilename)
                      (slurp url))
        all-users   (parse-out-all-users content)
        user-colors (assign-colors-to-users all-users
                                            all-other-colors
                                            custom-user-colors)]
    (render-log-as-html outfilename
                        (str "#" channel " on " date)  ; title
                        content
                        user-colors)))

(defn parse-out-all-users
  [content]
  (distinct (remove nil?
                    (for [line (str/split-lines content)]
                      (if-let [uname (or (re-find #"^\[\d\d:\d\d:\d\d\] ([\w_|^`\\-]+): " line)
                                         (re-find #"^\[\d\d:\d\d:\d\d\] \*([\w_|^`\\-]+) " line))]
                        (uname 1))))))

(defn assign-colors-to-users
  [users colors custom-user-colors]
  (merge (zipmap (remove (set (keys custom-user-colors)) users)
                 (cycle colors))
         custom-user-colors))

(declare parse-log)

(defn render-log-as-html
  "`content` is one big long string --- the plain text
content of the irc log."
  [outfilename title content user-colors]
  (let [header      (str/replace html-header "{{title}}" title)
        colors-demo (str/join "\n" (for [color all-other-colors]
                                     (str "<span style=\"color:" color "\">" color "</span>")))
        header      (str/replace header "{{colors}}" colors-demo)]
    (spit outfilename
          (str header
               (parse-log content user-colors)
               html-footer))))

(declare rowify-line)

(defn parse-log
  "You pass it a big chunk of plain text irc log, and it
gives you back all the lines (big chunk of text) as rows
in an html table."
  [log-text user-colors]
  (let [lines (str/split-lines log-text)
        rows  (map #(rowify-line % user-colors) lines)]
    (str/join "\n" rows)))

(declare extract-time)
(declare extract-author)
(declare extract-comment)

(defn rowify-line
  [line user-colors]
  (let [tr-id     (if-let [found-it (re-find #"^\[(\d\d:\d\d:\d\d)\]" line)]
                    (found-it 1))
        bot-name  (if-let [found-bot (re-find #"\[\d\d:\d\d:\d\d\] (lazybot|clojurebot): " line)]
                    (found-bot 1))
        id-in-tag (if tr-id (str " id=\"" tr-id "\" "))
        cl-in-tag (if bot-name (str " class=\"" bot-name "\" "))]
    ;; There are some rare cases where there's no timestamp.
    ;; Shield you eyes:
    (str "<tr" id-in-tag cl-in-tag ">"
         "<td><span class=\"timestamp\">" (if tr-id (str "<a href=\"#" tr-id "\">")) (extract-time line) (if tr-id "</a>") "</span></td>"
         "<td>" (extract-author  line user-colors) "</td>"
         "<td>" (extract-comment line user-colors) "</td></tr>")))

(defn extract-time
  [line]
  (str (or ((re-find #"^\[(\d\d:\d\d:\d\d)\]" line) 1)
           "&nbsp;")))

(defn extract-author
  [line user-colors]
  (if-let [found (re-find #"^\[\d\d:\d\d:\d\d\] ([\w_|^`\\-]+): " line)]
    (let [uname (found 1)]
      (str "<span style=\"color:" (user-colors uname) "\">" uname "</span>"))
    (if-let [found2 (re-find #"^\[\d\d:\d\d:\d\d\] \*([\w_|^`\\-]+) " line)]
      (let [uname (found2 1)]
        (str "<span class=\"comment\" style=\"color:" (user-colors uname) "\">* " uname "</span>"))
      ;; Otherwise, we can't find a username. Just leave this field blank.
      "&nbsp;")))

(declare urlify)

(defn extract-comment
  [line user-colors]
  (if-let [found (re-find #"^\[\d\d:\d\d:\d\d\] ([\w_|^`\\-]+): (.*)$" line)]
    ;; A regular irc comment (for some definition of regular).
    (let [uname (found 1)
          cmt   (found 2)]
      (str "<span style=\"color:" (user-colors uname) "\">" (urlify (hu/escape-html cmt)) "</span>"))
    (if-let [found2 (re-find #"^\[\d\d:\d\d:\d\d\] \*([\w_|^`\\-]+) (.*)$" line)]
      ;; A "*foo" style comment (Ex. user typed "/me hears something").
      (let [uname (found2 1)
            cmt   (found2 2)]
        (str "<span class=\"comment\" style=\"color:" (user-colors uname) "\">" (urlify (hu/escape-html cmt)) "</span>"))
      ;; Otherwise, maybe one of the bots is providing some output.
      (urlify (hu/escape-html line)))))

(defn urlify
  [text]
  (str/replace text
               #"(https?://\S+)"
               "<a href=\"$1\">$1</a>"))
