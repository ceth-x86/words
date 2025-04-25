(ns my-app.import
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
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

(defn is-meaning-delimiter? [line]
  (and (not (str/blank? line))
       (or (str/includes? line "---")
           (str/includes? line "===="))))

(defn parse-meaning [meaning-lines]
  (when (not-empty meaning-lines)
    (let [first-line (first meaning-lines)
          word-data (extract-word-and-transcription first-line)
          rest-lines (rest meaning-lines)
          translation-line (first rest-lines)
          translation (extract-translation translation-line)
          example-lines (filter is-example-line? (rest rest-lines))
          examples (map #(str/replace % #"^-\s*" "") example-lines)]
      (assoc word-data
             :translation translation
             :examples (vec examples)))))

(defn split-into-meanings [lines]
  (loop [remaining lines
         current-meaning []
         meanings []]
    (if (empty? remaining)
      (if (empty? current-meaning)
        meanings
        (conj meanings current-meaning))
      (let [line (first remaining)
            rest-lines (rest remaining)]
        (cond
          ;; New word entry starts with **
          (and (not (empty? current-meaning))
               (str/starts-with? line "**"))
          (recur rest-lines
                 [line]
                 (if (empty? current-meaning)
                   meanings
                   (conj meanings current-meaning)))
          
          ;; Delimiter for different meanings of the same word
          (is-meaning-delimiter? line)
          (recur rest-lines
                 []
                 (if (empty? current-meaning)
                   meanings
                   (conj meanings current-meaning)))
          
          ;; Empty line - may separate entries or be part of the current entry
          (str/blank? line)
          (recur rest-lines
                 current-meaning
                 meanings)
          
          ;; Continue building current meaning
          :else
          (recur rest-lines
                 (conj current-meaning line)
                 meanings))))))

(defn group-meanings-by-word [meanings]
  (let [word-meanings (group-by :word meanings)]
    (mapv (fn [[word meanings-list]]
            {:word word
             :meanings (vec meanings-list)})
          word-meanings)))

(defn parse-json-words-file [file-content]
  (try
    (json/read-str file-content :key-fn keyword)
    (catch Exception e
      (println "Error parsing JSON file:" (.getMessage e))
      [])))

(defn parse-markdown-words-file [file-content]
  (try
    (let [lines (str/split-lines file-content)
          words (atom [])
          current-word (atom nil)
          current-meaning (atom nil)
          in-examples (atom false)]
      
      (println "Parsing markdown file with" (count lines) "lines")
      
      (doseq [line lines]
        (cond
          ;; Skip title line or empty lines
          (or (str/blank? line) (str/starts-with? line "# "))
          nil
          
          ;; Word line (- **word** [transcription])
          (re-matches #"\s*-\s+\*\*(.*?)\*\*\s+\[(.*?)\].*" line)
          (let [[_ word transcription] (re-find #"\s*-\s+\*\*(.*?)\*\*\s+\[(.*?)\].*" line)]
            (when @current-word
              (when @current-meaning
                (swap! current-word update :meanings conj @current-meaning)
                (reset! current-meaning nil))
              (swap! words conj @current-word))
            (reset! current-word {:word word 
                                 :transcription transcription 
                                 :meanings []})
            (reset! current-meaning nil)
            (reset! in-examples false))
          
          ;; Meaning line
          (and @current-word 
               (re-matches #"\s+-\s+\*\*Meaning\*\*:\s+(.*)" line))
          (do
            (when @current-meaning
              (swap! current-word update :meanings conj @current-meaning))
            (let [description (second (re-find #"\s+-\s+\*\*Meaning\*\*:\s+(.*)" line))]
              (reset! current-meaning {:description description
                                      :examples []})
              (reset! in-examples false)))
          
          ;; Translation line
          (and @current-meaning 
               (re-matches #"\s+-\s+\*\*Translation\*\*:\s+(.*)" line))
          (let [translation (second (re-find #"\s+-\s+\*\*Translation\*\*:\s+(.*)" line))]
            (swap! current-meaning assoc :translation translation)
            (reset! in-examples false))
          
          ;; Examples header
          (and @current-meaning 
               (re-matches #"\s+-\s+\*\*Examples\*\*:\s*" line))
          (reset! in-examples true)
          
          ;; Example line
          (and @current-meaning @in-examples
               (re-matches #"\s+-\s+(.*)" line))
          (let [example (second (re-find #"\s+-\s+(.*)" line))]
            (swap! current-meaning update :examples conj example))))
      
      ;; Add the last meaning if it exists
      (when @current-meaning
        (swap! current-word update :meanings conj @current-meaning))
      
      ;; Add the last word if it exists
      (when @current-word
        (swap! words conj @current-word))
      
      (println "Found" (count @words) "words in markdown file")
      @words)
    (catch Exception e
      (println "Error parsing Markdown file:" (.getMessage e))
      (println (.getStackTrace e))
      [])))

(defn detect-file-format [file-path]
  (let [extension (last (str/split file-path #"\."))]
    (cond
      (= "json" extension) :json
      (= "md" extension) :markdown
      :else :unknown)))

(defn parse-words-file [file-content file-format]
  (case file-format
    :json (parse-json-words-file file-content)
    :markdown (parse-markdown-words-file file-content)
    (do
      (println "Unknown file format, attempting to parse as markdown")
      (parse-markdown-words-file file-content))))

(defn import-words-from-file [file-path]
  (try
    (let [file-content (slurp file-path)
          file-format (detect-file-format file-path)
          words-data (parse-words-file file-content file-format)
          word-count (count words-data)
          meaning-count (reduce + (map #(count (:meanings %)) words-data))]
      (println (str "Found " word-count " words with " meaning-count " total meanings for import."))
      
      (doseq [{:keys [word transcription meanings]} words-data]
        (doseq [{:keys [description translation examples]} meanings]
          (db/insert-word! word 
                           transcription 
                           description 
                           translation 
                           (str/join "\n" examples))))
      
      word-count)
    (catch Exception e
      (println (str "Error importing from file " file-path ":"))
      (println (.getMessage e))
      0))) 