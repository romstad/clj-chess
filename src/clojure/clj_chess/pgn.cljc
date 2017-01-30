(ns clj-chess.pgn
  "PGN parser."
  #?(:cljs (:require [clojure.string :as str]
                     [jschess.pgn :as pgn])
     :clj (:require [clojure.java.io :as io]
                    [clojure.string :as str]))
  #?(:clj (:import chess.PGNReader
                   chess.PGNToken$TokenType
                   (java.io PushbackReader StringReader))))

(defn token-type [token]
  #?(:clj (.getTokenType token)
     :cljs (.-type token)))

(defn token-value [token]
  #?(:clj  (.getValue token)
     :cljs (.-value token)))

(def token-type-string #?(:clj  PGNToken$TokenType/STRING
                          :cljs pgn/TokenTypeString))
(def token-type-integer #?(:clj  PGNToken$TokenType/INTEGER
                           :cljs pgn/TokenTypeInteger))
(def token-type-period #?(:clj  PGNToken$TokenType/PERIOD
                          :cljs pgn/TokenTypePeriod))
(def token-type-asterisk #?(:clj  PGNToken$TokenType/ASTERISK
                            :cljs pgn/TokenTypeAsterisk))
(def token-type-left-bracket #?(:clj  PGNToken$TokenType/LEFT_BRACKET
                                :cljs pgn/TokenTypeLeftBracket))
(def token-type-right-bracket #?(:clj  PGNToken$TokenType/RIGHT_BRACKET
                                 :cljs pgn/TokenTypeRightBracket))
(def token-type-left-paren #?(:clj  PGNToken$TokenType/LEFT_PAREN
                              :cljs pgn/TokenTypeLeftParen))
(def token-type-right-paren #?(:clj  PGNToken$TokenType/RIGHT_PAREN
                               :cljs pgn/TokenTypeRightParen))
(def token-type-left-angle #?(:clj  PGNToken$TokenType/LEFT_ANGLE
                              :cljs pgn/TokenTypeLeftAngle))
(def token-type-right-angle #?(:clj  PGNToken$TokenType/RIGHT_ANGLE
                               :cljs pgn/TokenTypeRightAngle))
(def token-type-nag #?(:clj  PGNToken$TokenType/NAG
                       :cljs pgn/TokenTypeNAG))
(def token-type-symbol #?(:clj  PGNToken$TokenType/SYMBOL
                          :cljs pgn/TokenTypeSymbol))
(def token-type-comment #?(:clj  PGNToken$TokenType/COMMENT
                           :cljs pgn/TokenTypeComment))
(def token-type-line-comment #?(:clj  PGNToken$TokenType/LINE_COMMENT
                                :cljs pgn/TokenTypeLineComment))
(def token-type-eof #?(:clj  PGNToken$TokenType/EOF
                       :cljs pgn/TokenTypeEOF))

(defn pgn-token-seq [pgn-reader]
  (let [token (.readToken pgn-reader)]
    (when-not (= (token-type token) token-type-eof)
      (cons token (lazy-seq (pgn-token-seq pgn-reader))))))

(defn pprint-token [token]
  (prn [(token-type token) (token-value token)]))

(defn termination-marker? [x]
  (and (vector? x)
       (= (count x) 2)
       (= (first x) :termination-marker)))

(defmulti process-movetext-token (fn [tokens _]
                                   (token-type (first tokens))))

(defmethod process-movetext-token :default [tokens acc]
  [(rest tokens) acc])

(defmethod process-movetext-token token-type-symbol
  [tokens acc]
  (let [val (if (.terminatesGame (first tokens))
              [:termination-marker (token-value (first tokens))]
              (token-value (first tokens)))]
    [(rest tokens) (conj acc val)]))

(defmethod process-movetext-token token-type-comment
  [tokens acc]
  [(rest tokens)
   (conj acc [(if (#{:moves :variation} (last acc)) :pre-comment :comment)
              (token-value (first tokens))])])

(defmethod process-movetext-token token-type-nag
  [tokens acc]
  [(rest tokens) (conj acc [:nag (token-value (first tokens))])])

(declare read-movetext)

(defmethod process-movetext-token token-type-left-paren
  [tokens acc]
  (let [[new-tokens variation] (read-movetext (rest tokens) [:variation])]
    [(rest new-tokens) (conj acc variation)]))

(defn read-movetext [tokens & [acc]]
  (let [acc (or acc [:moves])]
    (if (or (empty? tokens)
            (= (token-type (first tokens)) token-type-right-paren)
            (= (token-type (first tokens)) token-type-left-bracket))
      [tokens acc]
      (let [[new-tokens new-acc]
            (process-movetext-token tokens acc)]
        (read-movetext new-tokens new-acc)))))

(defn read-tag [tokens acc]
  (let [[_ tag-name tag-value right-bracket] tokens]
    (if (and (= (token-type tag-name) token-type-symbol)
             (= (token-type tag-value) token-type-string)
             (= (token-type right-bracket) token-type-right-bracket))
      [(drop 4 tokens) (conj acc [(token-value tag-name)
                                  (token-value tag-value)])]
      (throw (chess.PGNException. "Malformed tag pair")))))

(defn read-headers [token-seq & [acc]]
  (let [acc (or acc [:headers])]
    (if (or (empty? token-seq)
            (not= (token-type (first token-seq))
                  token-type-left-bracket))
      [token-seq acc]
      (let [[new-token-seq new-acc]
            (read-tag token-seq acc)]
        (read-headers new-token-seq new-acc)))))

(defn read-game [tokens]
  (let [[tokens headers] (read-headers tokens)
        [tokens movetext] (read-movetext tokens [:moves])]
    [tokens [:game headers movetext]]))

(defn parse-pgn [pgn]
  (second
    (read-game (pgn-token-seq
                 #?(:clj (PGNReader. (PushbackReader.
                                       (StringReader. pgn)))
                    :cljs (pgn/PGNReader. pgn))))))

(defn game-seq [tokens]
  (when-not (empty? tokens)
    (let [[tokens game] (read-game tokens)]
      (cons game (lazy-seq (game-seq tokens))))))

#?(:clj
   (defn games-in-file [pgn-file]
     (game-seq
       (pgn-token-seq (PGNReader. (PushbackReader.
                                    (io/reader pgn-file)))))))

(defn games-in-string [pgn-string]
  (game-seq
    (pgn-token-seq
      #?(:clj (PGNReader. (PushbackReader. (StringReader. pgn-string)))
         :cljs (pgn/PGNReader. pgn-string)))))

(defn shift-san-move-left [san-move-string]
  (let [translate {\b \a, \c \b, \d \c, \e \d, \f \e, \g \f, \h \g}]
    (apply str
           (map (fn [c] (get translate c c))
                san-move-string))))

(defn shift-moves-left [pgn-file-name]
  (let [games (games-in-file pgn-file-name)]
    (map (fn [g]
           [(first g) (second g)
            [:moves (map shift-san-move-left
                         (rest (nth g 2)))]])
         games)))
