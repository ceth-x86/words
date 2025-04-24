(ns my-app.import
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [my-app.db :as db]))

(defn extract-word-and-transcription [line]
  (let [word-match (re-find #"\*\*(.*?)\*\*" line)
        word (when word-match (second word-match))
        transcription-match (re-find #"/([^/]+)/" line)
        transcription (when transcription-match (second transcription-match))
        description-match (re-find #"-\s*(.+)$" line)
        description (when description-match (second description-match))]
    {:word word
     :transcription transcription
     :description description}))

(defn extract-translation [line]
  (let [translation-match (re-find #"\(\s*([^)]+)\s*\)" line)]
    (when translation-match
      (second translation-match))))

(defn is-example-line? [line]
  (and (not (str/blank? line))
       (str/starts-with? line "-")))

(defn parse-word-entry [lines]
  (when (not-empty lines)
    (let [first-line (first lines)
          word-data (extract-word-and-transcription first-line)
          rest-lines (rest lines)
          translation-line (first rest-lines)
          translation (extract-translation translation-line)
          example-lines (filter is-example-line? (rest rest-lines))
          examples (str/join "\n" (map #(str/replace % #"^-\s*" "") example-lines))]
      (assoc word-data
             :translation translation
             :examples examples))))

(defn parse-words-file [file-content]
  (let [lines (str/split-lines file-content)
        word-blocks (partition-by str/blank? lines)
        non-empty-blocks (filter #(not (every? str/blank? %)) word-blocks)
        word-entries (map parse-word-entry non-empty-blocks)]
    (filter #(and (:word %) (:translation %)) word-entries)))

(defn import-words-from-file [file-path]
  (try
    (let [file-content (slurp file-path)
          words-data (parse-words-file file-content)]
      (println (str "Found " (count words-data) " words for import."))
      (db/batch-import-words! words-data)
      (count words-data))
    (catch Exception e
      (println (str "Error importing from file " file-path ":"))
      (println (.getMessage e))
      0))) 