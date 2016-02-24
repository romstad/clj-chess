(ns clj-chess.uci
  "Functions for interacting with UCI chess engines."
  (:require [clojure.string :as string]
            [instaparse.core :as ip]
            [me.raynes.conch.low-level :as sh])
  (:import (java.io InputStreamReader BufferedReader)))

(alter-var-root #'*out* (constantly *out*))

(defn- option-as-map [& option-components]
  (let [map (into {} option-components)]
    [:option
     (if (= "spin" (map :type))
       (assoc map :default (read-string (map :default)))
       map)]))

(def uci-transform-options
  {:name (fn [& rest] [:name (apply print-str rest)])
   :number read-string
   :option option-as-map
   :id (fn [& rest] [:id (into {} rest)])
   :pv (fn [& rest] [:pv (vec rest)])
   :info (fn [& rest] [:info (into {} rest)])})

(def uci-info-parser
  (ip/parser
    "uci = id | uciok | readyok | option | bestmove | info

    id = <'id'> <whitespace> (engine-name | author)
    engine-name = <'name'> <whitespace> #'([\\SS\\ss])+'
    author= <'author'> <whitespace> #'([\\SS\\ss])+'

    uciok = <'uciok'> {<whitespace>}
    readyok = <'readyok'> {<whitespace>}

    option = name type [default] [min] [max] (*[var]*)
    name = <'option'> <whitespace> <'name'> <whitespace> namestr
    <namestr> = (!'type' #'[\\S]+' <whitespace>)+ | Epsilon
    type = <'type'> <whitespace> ('spin' | 'check' | 'button' | 'string' | 'combo') <whitespace>*
    default = <'default'> <whitespace> defstr <whitespace>*
    <defstr> = (!('min'|'max'|'var') #'[\\S]+' {<whitespace>})+ | Epsilon
    min = <'min'> <whitespace> number {<whitespace>}
    max = <'max'> <whitespace> number {<whitespace>}

    bestmove = <'bestmove'> <whitespace> move [<whitespace> <'ponder'> <whitespace> move] {<whitespace>}
    <move> = #'([a-z0-9])+'

    info = <'info'> (<whitespace> component)* (<whitespace>)*
    <component> = nodes | nps | depth | seldepth | time | multipv | score | tbhits | hashfull | pv | currmove | currmovenumber
    whitespace = #'\\s+'
    nodes = <'nodes'> <whitespace> number
    nps = <'nps'> <whitespace> number
    depth = <'depth'> <whitespace> number
    seldepth = <'seldepth'> <whitespace> number
    time = <'time'> <whitespace> number
    multipv = <'multipv'> <whitespace> number
    score = <'score'> <whitespace> (cp | mate)
    cp = <'cp'> <whitespace> number [<whitespace> (upperbound|lowerbound)]
    mate = <'mate'> <whitespace> number [<whitespace> (upperbound|lowerbound)]
    upperbound = <'upperbound'>
    lowerbound = <'lowerbound'>
    tbhits = <'tbhits'> <whitespace> number
    hashfull = <'hashfull'> <whitespace> number
    currmove = <'currmove'> <whitespace> move
    currmovenumber = <'currmovenumber'> <whitespace> number
    pv = <'pv'> moves (<whitespace>)*
    <moves> = (<whitespace> move)*
    number = #'-?[0-9]+'"))

(defn parse-uci-output
  "Parse an output line from a UCI chess engine."
  [output]
  (let [p (uci-info-parser output)]
    (when-not (ip/failure? p)
      (second (ip/transform uci-transform-options p)))))


(defn- get-uci-options
  "Ask for and parse the options for a UCI chess engine."
  [engine]
  (binding [*in* (-> engine :out InputStreamReader. BufferedReader.)]
    (sh/feed-from-string engine "uci\n")
    (loop [id []
           options []]
      (let [output (parse-uci-output (read-line))]
        (cond
          (nil? output) (recur id options)
          (= :uciok (first output)) {:id id :options options}

          (= :id (first output))
          (recur (conj id (second output)) options)

          (= :option (first output))
          (recur id (conj options (second output)))

          :else (recur id options))))))

(defn send-command
  "Sends a UCI command (as a string) to a chess engine."
  [engine command]
  (sh/feed-from-string (:process engine) (str command "\n")))

(defn follow-process-output
  "Starts up a thread following the output of a process, and calls the
  provided function 'action' on every output line. The thread keeps running
  until the process stops running or 'action' returns :finished."
  [process action]
  (future
    (binding [*in* (-> process :out InputStreamReader. BufferedReader.)]
      (loop []
        (when-let [line (read-line)]
          (when-not (= :finished (action line))
            (recur)))))))

(defn think
  "Sends the engine the supplied UCI 'go' command, and starts a new thread to
  monitor the search output. When the search is completed, the supplied function
  'bestmove-action' is called with the engine output (a string of the form
  'bestmove xxxx ponder yyyy') as the input parameter, and the monitoring
  thread exits. The optional 'info-action' parameter is a function which is
  called on all 'info' output lines that occur during the search."
  [engine go-command bestmove-action & [info-action]]
  (send-command engine go-command)
  (follow-process-output
    (:process engine)
    (fn [engine-output]
      (cond
        (and info-action (.startsWith engine-output "info"))
        (info-action engine-output)

        (.startsWith engine-output "bestmove")
        (do
          (bestmove-action engine-output)
          :finished)))))

(defn run-engine
  "Starts the UCI chess engine at the given path name, and returns an engine
  object (a map) that can be used as argument to the send-command and think
  functions."
  [engine-path & [output-action]]
  (let [engine (sh/proc engine-path)
        options (get-uci-options engine)]
    (when output-action
      (follow-process-output engine output-action))
    (merge options {:process engine})))
