(ns colorize-irc-logs.core
  (:require [clojure.string :as str]
            [hiccup.util :as hu])
  (:gen-class))

;; ----------------------------------------------------------------------
;; TODO:
;;
;;   * Ooof! Disentangle functions that extract timestamp, name, and comment
;;     from functions that generate html for those things.
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
                         "lazybot"     "#136438"
                         })

(def all-other-colors ["#155A7C"
                       "#911114"
                       "#AB257F"
                       "#592EAB"
                       "#127950"
                       "#625809"
                       "#7726A0"
                       "#2A50A4"
                       "#59780C"
                       "#0B7509"
                       "#A222A7"
                       "#127876"])

(def base-url "http://www.raynes.me/logs/irc.freenode.net/")

(def html-header "<!doctype html>
<html>
<head>
<title>irc log: {{title}}</title>
<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />
<style type=\"text/css\">
body {
    background-color: #888;
}

#main-box {
    width: 900px;
    background-color: #f8f8f8;
    margin: 20px auto 20px auto;
    padding: 2px 20px 20px 20px;
}

table {
    border-collapse: collapse;
    width: 100%;
}

th, tr, td {
    padding: 2px;
}

td {
    vertical-align: top;
    padding: 6px 4px 6px 4px;
}

th {
    background-color: #ddd;
}

a:link  { color: #555; }

.timestamp { font-size: small; font-family: monospace; color: #888; }
.timestamp a:link  { text-decoration: none; color: #888; }
.timestamp a:hover { text-decoration: underline; }

.author { text-align: right; padding-right: 12px; font-weight: bold; }
.comment { font-style: italic; }

.clojurebot, .lazybot { font-family: monospace; }
.lazybot    { background-color: #E1F6EA; }
.clojurebot { background-color: #DEE8FA; }
</style>

<body>
<div id=\"main-box\">
<h1>IRC Log for {{title}}</h1>
<p><a href=\"../index.html\">back to master index</a></p>
<table>
")

(def html-footer "
</table>
</div>
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
  (let [header (str/replace html-header "{{title}}" title)]
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
        ;; We need to know who posted the previous line, so we can remove the author name
        ;; in this line if it's the same name.
        rows  (map (fn [line prev-line]
                     (rowify-line line
                                  prev-line
                                  user-colors))
                   lines
                   (cons "The elusive zeroth line!" lines))]
    (str/join "\n" rows)))

(declare extract-time)
(declare extract-author)
(declare extract-comment)
(declare extract-just-the-name)

(defn rowify-line
  [line prev-line user-colors]
  ;;    the table-row id (which is the timestamp)
  (let [tr-id          (if-let [found-it (re-find #"^\[(\d\d:\d\d:\d\d)\]" line)]
                         (found-it 1))
        bot-name       (if-let [found-bot (re-find #"\[\d\d:\d\d:\d\d\] (lazybot|clojurebot): " line)]
                         (found-bot 1))
        id-in-tag      (if tr-id (str " id=\"" tr-id "\" "))
        cl-in-tag      (if bot-name (str " class=\"" bot-name "\" "))
        prev-line-name (extract-just-the-name prev-line)
        this-line-name (extract-just-the-name line)]
    ;; There are some rare cases where there's no timestamp.
    ;; Shield you eyes:
    (str "<tr" id-in-tag cl-in-tag ">"
         "<td><span class=\"timestamp\">" (if tr-id (str "<a href=\"#" tr-id "\">")) (extract-time line) (if tr-id "</a>") "</span></td>"
         "<td class=\"author\">" (if (= this-line-name prev-line-name) "&nbsp;" (extract-author line user-colors)) "</td>"
         "<td>" (extract-comment line user-colors) "</td></tr>")))

(defn extract-just-the-name
  [line]
  (if-let [found (re-find #"\[\d\d:\d\d:\d\d\] ([\w_|^`\\-]+): " line)]
    (found 1)
    (if-let [found (re-find #"\[\d\d:\d\d:\d\d\] \*([\w_|^`\\-]+) " line)]
      (found 1))))

(defn extract-time
  [line]
  (str (or ((re-find #"^\[(\d\d:\d\d:\d\d)\]" line) 1)
           "&nbsp;")))

(defn extract-author
  [line user-colors]
  (if-let [found (re-find #"^\[\d\d:\d\d:\d\d\] ([\w_|^`\\-]+): " line)]
    (let [uname (found 1)]
      (str "<span style=\"color:" (user-colors uname) "\">" uname ":</span>"))
    (if-let [found2 (re-find #"^\[\d\d:\d\d:\d\d\] \*([\w_|^`\\-]+) " line)]
      (let [uname (found2 1)]
        (str "<span class=\"comment\" style=\"color:" (user-colors uname) "\">* " uname ":</span>"))
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
               "<i><a href=\"$1\">$1</a></i>"))
