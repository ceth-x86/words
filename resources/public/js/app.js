const API_BASE_URL = '/api';

new Vue({
    el: '#app',
    data: {
        title: 'English Words Dictionary',
        words: [],
        searchTerm: '',
        searched: false,
        stats: null,
        showAddWordForm: false,
        showAddMeaningForm: {},
        newWord: {
            word: '',
            transcription: '',
            description: '',
            translation: '',
            examples: ''
        },
        newMeaning: {
            transcription: '',
            description: '',
            translation: '',
            examples: ''
        },
        newExamples: {},
        notification: {
            show: false,
            message: '',
            type: 'success', // success, error, warning
            timeout: null
        }
    },
    methods: {
        // API Calls
        getAllWords() {
            this.searchTerm = '';
            this.searched = false;
            axios.get(`${API_BASE_URL}/words`)
                .then(response => {
                    this.words = response.data.words;
                    this.stats = {
                        count: response.data.count,
                        total_meanings: response.data.total_meanings,
                        total_examples: response.data.total_examples
                    };
                })
                .catch(error => {
                    this.showNotification(`Error loading words: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        searchWords() {
            if (!this.searchTerm.trim()) {
                this.getAllWords();
                return;
            }
            
            this.searched = true;
            axios.get(`${API_BASE_URL}/search?term=${encodeURIComponent(this.searchTerm)}`)
                .then(response => {
                    this.words = response.data.words;
                    this.stats = { count: response.data.count };
                })
                .catch(error => {
                    this.showNotification(`Search error: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        addWord() {
            if (!this.newWord.word || !this.newWord.translation) {
                this.showNotification('Word and translation are required', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/words`, this.newWord)
                .then(response => {
                    this.showNotification(`Word "${this.newWord.word}" added successfully`, 'success');
                    // Clear form
                    this.resetNewWord();
                    this.showAddWordForm = false;
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error adding word: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        addMeaning(wordStr) {
            if (!this.newMeaning.translation) {
                this.showNotification('Translation is required', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/words/${encodeURIComponent(wordStr)}/meanings`, this.newMeaning)
                .then(response => {
                    this.showNotification(`New meaning added to "${wordStr}"`, 'success');
                    // Clear form
                    this.resetNewMeaning();
                    Vue.set(this.showAddMeaningForm, response.data.word.id, false);
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error adding meaning: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        addExample(meaningId) {
            const exampleText = this.newExamples[meaningId];
            if (!exampleText || !exampleText.trim()) {
                this.showNotification('Example text cannot be empty', 'warning');
                return;
            }
            
            axios.post(`${API_BASE_URL}/meanings/${meaningId}/examples`, { text: exampleText })
                .then(response => {
                    this.showNotification('Example added successfully', 'success');
                    // Clear the input
                    Vue.set(this.newExamples, meaningId, '');
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error adding example: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        deleteWord(wordStr) {
            if (!confirm(`Are you sure you want to delete "${wordStr}" and all its meanings?`)) {
                return;
            }
            
            axios.delete(`${API_BASE_URL}/words/${encodeURIComponent(wordStr)}`)
                .then(response => {
                    this.showNotification(`Word "${wordStr}" deleted successfully`, 'success');
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error deleting word: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        deleteMeaning(meaningId) {
            if (!confirm('Are you sure you want to delete this meaning?')) {
                return;
            }
            
            axios.delete(`${API_BASE_URL}/meanings/${meaningId}`)
                .then(response => {
                    this.showNotification('Meaning deleted successfully', 'success');
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error deleting meaning: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        deleteExample(exampleId) {
            axios.delete(`${API_BASE_URL}/examples/${exampleId}`)
                .then(response => {
                    this.showNotification('Example deleted successfully', 'success');
                    // Refresh word list
                    this.getAllWords();
                })
                .catch(error => {
                    this.showNotification(`Error deleting example: ${this.getErrorMessage(error)}`, 'error');
                });
        },
        
        // UI Helpers
        resetNewWord() {
            this.newWord = {
                word: '',
                transcription: '',
                description: '',
                translation: '',
                examples: ''
            };
        },
        
        resetNewMeaning() {
            this.newMeaning = {
                transcription: '',
                description: '',
                translation: '',
                examples: ''
            };
        },
        
        showNotification(message, type = 'success') {
            // Clear any existing timeout
            if (this.notification.timeout) {
                clearTimeout(this.notification.timeout);
            }
            
            // Set notification data
            this.notification.message = message;
            this.notification.type = type;
            this.notification.show = true;
            
            // Set timeout to hide notification
            this.notification.timeout = setTimeout(() => {
                this.notification.show = false;
            }, 3000);
        },
        
        getErrorMessage(error) {
            if (error.response && error.response.data && error.response.data.error) {
                return error.response.data.error;
            }
            return error.message || 'Unknown error';
        }
    },
    mounted() {
        // Load words when the component is mounted
        this.getAllWords();
    }
}); 