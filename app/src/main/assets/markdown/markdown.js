
// import { marked } from 'https://cdn.jsdelivr.net/npm/marked@18.1.1/+esm';
// import DOMPurify  from 'https://cdn.jsdelivr.net/npm/dompurify@3.4.17/+esm';

// curl 'https://cdn.jsdelivr.net/npm/marked@18.1.1/+esm'    -s -o marked.esm.js 
// curl 'https://cdn.jsdelivr.net/npm/dompurify@3.4.17/+esm' -s -o dompurify.esm.js 
import { marked } from './marked.esm.js';
import DOMPurify  from './dompurify.esm.js';

class MarkdownTextElement extends HTMLElement {
    connectedCallback() {
        const src = this.getAttribute('src');
        if (src) {
            fetch(src)
                .then(response => response.text())
                .then(text => this._renderMarkdown(text))
                .catch(err => console.error('Failed to load:', err));
            return;
        }
        const script = this.querySelector('script[type="text/markdown"]');
        if (script) {
            this._renderMarkdown(this._dedent(script.textContent));
        }
    }

    // Render markdown supplied programmatically as a plain string.
    // The text is never serialized to HTML, so it cannot break out of any markup.
    set markdown(value) {
        this._renderMarkdown(value);
    }

    // Strip the minimum indentation found across all non-empty lines
    _dedent(text) {
        const lines = text.split('\n');
        const minIndent = lines
            .filter(line => line.trim().length > 0)
            .reduce((min, line) => Math.min(min, line.match(/^ */)[0].length), Infinity);
        return lines
            .map(line => line.slice(minIndent))
            .join('\n')
            .trim();
    }

    _renderMarkdown(text) {
        console.log('Got markdown: ', text.length, ' characters');
        this.innerHTML = DOMPurify.sanitize(marked.parse(text));
    }
}

customElements.define('markdown-text', MarkdownTextElement);

// Not needed for modules or deferred scripts:
// document.addEventListener('DOMContentLoaded', () => {
//     // Define the custom element after the DOM is fully loaded.
//     // Otherwise, connectedCallback() is called before child nodes are available.
//     customElements.define('markdown-text', MarkdownTextElement);
// });
