(ns my-app.export
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [my-app.db :as db]))

(defn format-word-entry [{:keys [word transcription description translation examples]}]
  (let [first-line (str "**" word "** /" transcription "/ - " description)
        second-line (str "(" translation ")")
        example-lines (when (and examples (not (str/blank? examples)))
                        (map #(str "- " %) (str/split-lines examples)))
        all-lines (concat [first-line second-line] example-lines [""])]
    (str/join "\n" all-lines)))

(defn format-words-for-export [words]
  (str/join "\n" (map format-word-entry words)))

(defn export-words-to-file [file-path]
  (try
    (let [words (db/get-all-words)
          formatted-content (format-words-for-export words)]
      (println (str "Found " (count words) " words for export."))
      (spit file-path formatted-content)
      (println (str "Words successfully exported to file: " file-path))
      (count words))
    (catch Exception e
      (println (str "Error exporting to file " file-path ":"))
      (println (.getMessage e))
      0)))

(defn export-search-results-to-file [search-term file-path]
  (try
    (let [words (db/search-words search-term)
          formatted-content (format-words-for-export words)]
      (println (str "Found " (count words) " words for export."))
      (spit file-path formatted-content)
      (println (str "Search results successfully exported to file: " file-path))
      (count words))
    (catch Exception e
      (println (str "Error exporting search results to file " file-path ":"))
      (println (.getMessage e))
      0))) 