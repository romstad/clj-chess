(ns clj-chess.pgn
  "PGN parser. It turns out that using instaparse for this wasn't the best
  idea, since it's painfully slow. This entire namespace needs to be rewritten
  before it is usable."
  (:require [clojure.zip :as zip]
            [instaparse.core :as ip]
            [clj-chess.board :as board]
            [clj-chess.game :as game]))

(def pgn-parser
  (ip/parser
    "
    (* A PGN game consists of PGN headers followed by the move text. *)
    game = headers <whitespace> movetext
    headers = {header [<whitespace>]}

    (* Each header consists of a header name and a corresponding value, *)
    (* contained in a pair of square brackets. *)
    header = <'['> header-name <whitespace> header-value <']'>
    header-name = symbol
    header-value = string

    (* Movetext consists of moves (symbols), numbers, periods, comments,    *)
    (* variations, numeric annotation glyphs, and game termination markers. *)
    movetext = {(termination-marker / <number> / <'.'> / symbol / comment / variation / nag / semicolon-comment / percent-comment / <whitespace>)}

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
    <variation> = <'('> movetext <')'>

    nag = ('$' #'[0-9]+') | '!' | '?' | '!!' | '??' | '!?' | '?!'

    (* Game termination markers, '1-0', '0-1', '1/2-1/2' or '*', where *)
    (* the asterisk indicates an incomplete game or an unknown result. *)
    termination-marker = white-wins | black-wins | draw | unknown-result
    white-wins = <'1-0'>
    black-wins = <'0-1'>
    draw = <'1/2-1/2'>
    unknown-result = <'*'>

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
    symbol = symbol-start symbol-continuation
    <symbol-start> = #'[A-Za-z0-9]'
    <symbol-continuation> = #'[A-Za-z0-9_+\\-:=#]*'

    number = #'[0-9]+'
    whitespace = #'\\s+'
    "))

(def pgn-transform
  {:string str
   :symbol str
   :comment-content str
   :header (fn [name value]
             [(keyword (second name)) (second value)])
   :nag (fn [& rest]
          [:nag (case (first rest)
                  "!" 1
                  "?" 2
                  "!!" 3
                  "??" 4
                  "!?" 5
                  "?!" 6
                  "$" (read-string (second rest)))])})


(defn parse-pgn [pgn & [start]]
  (ip/transform pgn-transform (pgn-parser pgn :start start)))

(def pgn-string
  "[Event \"F/S Return Match\"]
[Site \"Belgrade, Serbia JUG\"]
[Date \"1992.11.04\"]
[Round \"29\"]
[White \"Fischer, Robert J.\"]
[Black \"Spassky, Boris V.\"]
[Result \"1/2-1/2\"]

1. e4 $1 e5? 2. Nf3 Nc6 3. Bb5 {The Ruy Lopez!} a6 (3... Nf6 4. O-O (4. d4 Bc5) Nxe4 5. d4) 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5 7. Bb3 d6 8. c3
O-O 9. h3 Nb8 10. d4 Nbd7 11. c4 c6 12. cxb5 axb5 13. Nc3 Bb7 14. Bg5 b4 15.
Nb1 h6 16. Bh4 c5 17. dxe5 Nxe4 18. Bxe7 Qxe7 19. exd6 Qf6 20. Nbd2 Nxd6 21.
Nc4 Nxc4 22. Bxc4 Nb6 23. Ne5 Rae8 24. Bxf7+ Rxf7 25. Nxf7 Rxe1+ 26. Qxe1 Kxf7 ; this is a test
27. Qe3 Qg5 28. Qxg5 hxg5 29. b3 Ke6 30. a3 Kd6 31. axb4 cxb4 32. Ra5 Nd5 33. % also a test
f3 Bc8 34. Kf2 Bf5 35. Ra7 g6 36. Ra6+ Kc5 37. Ke1 Nf4 38. g3 Nxh3 39. Kd2 Kb5
40. Rd6 Kc5 41. Ra6 Nf2 42. g4 Bd3 43. Re6 1/2-1/2")
