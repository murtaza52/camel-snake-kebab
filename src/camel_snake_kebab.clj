(ns camel-snake-kebab
  (:require [clojure.string :refer [split join capitalize lower-case upper-case]])
  (:import  (clojure.lang Keyword Symbol)))

(def ^:private upper-case-http-headers
  #{"CSP" "ATT" "WAP" "IP" "HTTP" "CPU" "DNT" "SSL" "UA" "TE" "WWW" "XSS" "MD5"})

(defn- capitalize-http-header [s]
  (or (upper-case-http-headers (upper-case s))
      (capitalize s)))

(def ^:private word-separator-pattern
  "A pattern that matches all known word separators."
  (->> ["\\s+" "_" "-"
        "(?<=[A-Z])(?=[A-Z][a-z])"
        "(?<=[^A-Z_-])(?=[A-Z])"
        "(?<=[A-Za-z])(?=[^A-Za-z])"]
       (join "|")
       re-pattern))

(defn- convert-case [first-fn rest-fn sep s]
  "Converts the case of a string according to the rule for the first
  word, remaining words, and the separator."
  (let [[first & rest] (split s word-separator-pattern)]
    (join sep (cons (first-fn first) (map rest-fn rest)))))

(def ^:private case-conversion-rules
  "The formatting rules for each case."
  {"CamelCase"        [capitalize capitalize "" ]
   "Camel_Snake_Case" [capitalize capitalize "_"]
   "camelCase"        [lower-case capitalize "" ]
   "Snake_case"       [capitalize lower-case "_"]
   "SNAKE_CASE"       [upper-case upper-case "_"]
   "snake_case"       [lower-case lower-case "_"]
   "kebab-case"       [lower-case lower-case "-"]
   "HTTP-Header-Case" [capitalize-http-header capitalize-http-header "-"]
   "CamelCaseWithSpace" [capitalize capitalize " "]})

(defprotocol AlterName
  (alter-name [this f] "Alters the name of this with f."))

(extend-protocol AlterName
  String  (alter-name [this f] (-> this f))
  Keyword (alter-name [this f] (-> this name f keyword))
  Symbol  (alter-name [this f] (-> this name f symbol)))

(doseq [[case-label [first-fn rest-fn sep]] case-conversion-rules]
  (let [case-converter (partial convert-case first-fn rest-fn sep)
        symbol-creator (fn [type-label]
                         (->> [case-label type-label]
                              (join \space)
                              case-converter
                              (format "->%s")
                              symbol))]
    ;; Create the type-preserving functions.
    (intern *ns*
            (->> case-label (format "->%s") symbol)
            #(alter-name % case-converter))
    ;; Create the string-returning functions.
    (intern *ns* (symbol-creator "string") (comp case-converter name))
    (doseq [[type-label type-converter] {"symbol" symbol "keyword" keyword}]
      ;; Create the symbol- and keyword-returning.
      (intern *ns*
              (symbol-creator type-label)
              (comp type-converter case-converter name)))))
