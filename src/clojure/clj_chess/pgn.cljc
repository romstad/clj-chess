(ns clj-chess.pgn
  "PGN parser. It turns out that using instaparse for this wasn't the best
  idea, since it's painfully slow. This entire namespace needs to be rewritten
  before it is usable."
  (:require [clojure.string :as str]
            [instaparse.core :as ip]
            #?(:cljs [cljs.reader :refer [read-string]])))

(def pgn-parser
  (ip/parser
    "
    (* A PGN game consists of PGN headers followed by the move text. *)
    game = headers <whitespace> moves
    headers = {header [<whitespace>]}

    (* Each header consists of a header name and a corresponding value, *)
    (* contained in a pair of square brackets. *)
    header = <'['> header-name <whitespace> header-value <']'>
    header-name = symbol
    header-value = string

    (* Movetext consists of moves (symbols), numbers, periods, comments,    *)
    (* variations, numeric annotation glyphs, and game termination markers. *)
    moves = {(termination-marker / <number> / <'.'> / symbol / comment / variation / nag / semicolon-comment / percent-comment / <whitespace>)}

    (* Comments are arbitrary strings surrounded by curly braces. *)
    comment = <'{'> comment-content <'}'>
    comment-content = {!'}' #'[\\S\\s]'}

    (* There are also two other styles of comment: Everything following a semicolon  *)
    (* or a percent character until the end of the line. I think those styles of     *)
    (* comments are not meant as game annotations, but rather as something analogous *)
    (* to source code comments. We therefore hide them from the parsed output.       *)
    <semicolon-comment> = <';'> <{!'\n' #'[\\S\\s]'}> <'\n'>
    <percent-comment> = <'%'> <{!'\n' #'[\\S\\s]'}> <'\n'>

    (* Variations are contained in parens, and can be nested recursively. *)
    <variation> = <'('> moves <')'>

    nag = ('$' #'[0-9]+') | '!' | '?' | '!!' | '??' | '!?' | '?!'

    (* Game termination markers, '1-0', '0-1', '1/2-1/2' or '*', where *)
    (* the asterisk indicates an incomplete game or an unknown result. *)
    termination-marker = '1-0' | '0-1' | '1/2-1/2' | '*'

    (* Strings are a little messy, since they are surrounded by double *)
    (* quotes, but we also allows a double quote within the string if *)
    (* preceded by a backslash escape character. *)
    string = <'\"'> string-contents <'\"'>
    <string-contents> = {!'\"' (escaped-quote | #'[\\S\\s]')}
    <escaped-quote> = <'\\\\'> '\"'

    (* A PGN symbol token starts with a letter or digit character and is *)
    (* immediately followed by a sequence of zero or more symbol continuation *)
    (* characters. These continuation characters are letters, digits, *)
    (* and the special character '_', '+', '-', '#', ':' and '='. *)
    <symbol> = #'[A-Za-z0-9][A-Za-z0-9_+\\-:=#]*'

    number = #'[0-9]+'
    whitespace = #'\\s+'
    "))

(def pgn-transform
  {:string str
   :symbol str
   :comment-content (comp #(str/replace % \newline \space) str)
   :header (fn [name value]
             [(second name) (second value)])
   :nag (fn [& rest]
          [:nag (case (first rest)
                  "!" 1
                  "?" 2
                  "!!" 3
                  "??" 4
                  "!?" 5
                  "?!" 6
                  "$" (read-string (second rest)))])})

#?(:cljs
   (defn- vectorize-parsed-pgn
     "Workaround for bug (?) in instaparse-cljs, where some parse tree entries
     turn out to be of type instaparse.auto-flatten-seq/FlattenOnDemandVector
     rather than vectors."
     [parsed-pgn]
     (cond
       (not (coll? parsed-pgn))
       parsed-pgn

       (= (type parsed-pgn) instaparse.auto-flatten-seq/FlattenOnDemandVector)
       (vec parsed-pgn)

       :else (vec (cons (first parsed-pgn)
                        (map vectorize-parsed-pgn (rest parsed-pgn)))))))

(defn parse-pgn [pgn & [start]]
  (let [parsed-pgn (pgn-parser pgn :start (or start :game))]
    (ip/transform pgn-transform
                  #?(:clj parsed-pgn
                     :cljs (vectorize-parsed-pgn parsed-pgn)))))

