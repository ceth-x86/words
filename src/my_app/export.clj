(ns my-app.export
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [my-app.db :as db]))

(defn format-examples [examples]
  (map #(str "      - " (:text %)) examples))

(defn format-meaning [{:keys [transcription description translation examples]}]
  (let [meaning-line (str "  - **Meaning**: " description)
        translation-line (str "    - **Translation**: " translation)
        examples-header "    - **Examples**:"
        example-lines (format-examples examples)
        all-lines (concat [meaning-line translation-line examples-header] example-lines)]
    (str/join "\n" all-lines)))

(defn format-word-entry [{:keys [word meanings]}]
  (let [word-header (str "- **" word "** [" (:transcription (first meanings)) "]")
        formatted-meanings (map format-meaning meanings)
        meanings-text (str/join "\n\n" formatted-meanings)]
    (str word-header "\n" meanings-text "\n\n")))

(defn format-words-for-export [words]
  (str "# Words\n\n" (str/join "" (map format-word-entry words))))

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