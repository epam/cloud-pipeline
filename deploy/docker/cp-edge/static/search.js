/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

let API = '${API_EXTERNAL}';
if (API.endsWith('/')) {
  API = API.slice(0, -1);
}

window.addEventListener('DOMContentLoaded', () => {
  let container;
  let documents = [];
  const url = `${API}/search`;
  const PAGE_SIZE = 50;
  const CSS_TEXT = `
    .cp-container,
    .cp-container * {
      box-sizing: border-box;
      font-family: sans-serif;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.65);
    }
    .cp-container {
      position:absolute;
      z-index: 9999;
      top: 10px;
      right: 10px;
      width: 50px;
      padding: 0;
      border: 1px solid #bfbfbf;
      border-radius: 4px;
      background: white;
      display: flex;
      flex-direction: column;
      transition: all 0.15s ease;
      max-height: calc(100vh - 40px);
      overflow: hidden;
    }
    .cp-container.expanded {
      width: 50vw;
      padding: 10px 15px;
    }
    .cp-search-controls {
      display: flex;
      flex-direction: row;
      flex-wrap: nowrap;
      align-items: center;
    }
    .cp-expand-btn:before {
      display: flex;
      padding: 10px 15px;
      content: url("data:image/svg+xml; utf8, %3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1024 1024' width='20' height='20'%3E%3Cpath fill='rgba(0,0,0,0.65)' d='M909.6 854.5L649.9 594.8C690.2 542.7 712 479 712 412c0-80.2-31.3-155.4-87.9-212.1-56.6-56.7-132-87.9-212.1-87.9s-155.5 31.3-212.1 87.9C143.2 256.5 112 331.8 112 412c0 80.1 31.3 155.5 87.9 212.1C256.5 680.8 331.8 712 412 712c67 0 130.6-21.8 182.7-62l259.7 259.6a8.2 8.2 0 0 0 11.6 0l43.6-43.5a8.2 8.2 0 0 0 0-11.6zM570.4 570.4C528 612.7 471.8 636 412 636s-116-23.3-158.4-65.6C211.3 528 188 471.8 188 412s23.3-116.1 65.6-158.4C296 211.3 352.2 188 412 188s116.1 23.2 158.4 65.6S636 352.2 636 412s-23.3 116.1-65.6 158.4z'/%3E%3C/svg%3E");
      width: 20px;
      height: 20px;
      cursor: pointer;
    }
    .cp-container.expanded .cp-expand-btn:before {
      padding: 4px;
      content: url("data:image/svg+xml; utf8, %3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1024 1024' width='20' height='20'%3E%3Cpath fill='rgba(0,0,0,0.65)' d='M563.8 512l262.5-312.9c4.4-5.2.7-13.1-6.1-13.1h-79.8c-4.7 0-9.2 2.1-12.3 5.7L511.6 449.8 295.1 191.7c-3-3.6-7.5-5.7-12.3-5.7H203c-6.8 0-10.5 7.9-6.1 13.1L459.4 512 196.9 824.9A7.95 7.95 0 0 0 203 838h79.8c4.7 0 9.2-2.1 12.3-5.7l216.5-258.1 216.5 258.1c3 3.6 7.5 5.7 12.3 5.7h79.8c6.8 0 10.5-7.9 6.1-13.1L563.8 512z'/%3E%3C/svg%3E");
    }
    .cp-container.expanded .cp-expand-btn {
      display: flex;
      width: 20px;
      justify-content: center;
      margin-left: 10px
    }
    .cp-search-input {
      outline: none;
      height:  28px;
      border: 1px solid #d9d9d9;
      border-radius: 4px;
      padding: 0 10px;
      width: 140px;
      display: none;
      margin-left: 5px;
      width: 0px;
      transition: all 0.25s ease;
      transform: scale(0);
      opacity: 0;
    }
    .cp-search-btn {
      cursor: pointer;
      background: #108ee9;
      color: white;
      margin-left: 5px;
      height: 28px;
      padding: 0 15px;
      border: none;
      border-radius: 4px;
      display: none;
      width: 0px;
      transition: all 0.15s ease;
      transform: scale(0);
      opacity: 0;
    }
    .cp-search-input.cp-pending,
    .cp-search-input:disabled
    .cp-search-input.cp-pending:hover,
    .cp-search-input:disabled:hover,
    .cp-search-btn.cp-pending,
    .cp-search-btn:disabled,
    .cp-search-btn.cp-pending:hover,
    .cp-search-btn:disabled:hover {
      color: rgba(0,0,0,.25);
      background-color: #f7f7f7;
      border-color: #d9d9d9;
    }
    .cp-load-more-btn.cp-pending,
    .cp-load-more-btn.cp-pending:hover {
      color: rgba(0,0,0,.25);
      border-color: #d9d9d9;
    }
    .cp-container.expanded .cp-search-input,
    .cp-container.expanded .cp-search-btn {
      transform: scale(1);
      display: block;
      opacity: 1;
    }
    .cp-container.expanded .cp-search-input {
      width: 100%;
    }
    .cp-container.expanded .cp-search-btn {
      width: 120px;
    }
    .cp-search-input:hover,
    .cp-search-input:focus {
      border-color: #49a9ee;
      box-shadow: 0 0 0 2px rgb(73 169 238 / 20%);
    }
    .cp-search-btn:hover {
      background: #49a9ee;
    }
    .cp-results-container {
      overflow-y: auto;
      overflow-x: hidden;
    }
    .cp-results-container.with-content {
      margin-top: 10px;
    }
    .cp-results {
      padding: 10px;
    }
    .cp-result-card {
      display: flex;
      flex-direction: column;
      padding: 10px;
      cursor: pointer;
      transition: box-shadow 0.15s ease;
      border-bottom: 1px solid #bfbfbf;
      margin: 0 3px;
      text-decoration: none;
    }
    .cp-result-card:hover {
      box-shadow: 0 1px 6px rgb(0 0 0 / 40%);
    }
    .cp-result-card-title {
      font-weight: bold;
    }
    .cp-highlights .cp-highlight {
      background: yellow;
    }
    .cp-highlights td.cp-name {
      white-space: nowrap;
      padding-left: 20px;
    }
    .cp-highlights td.cp-matches {
      width: 100%;
      padding-left: 20px;
      padding-right: 5px;
      word-break: break-word;
    }
    .cp-highlights td.cp-matches p {
      word-break: break-all;
      margin: 0;
    }
    .cp-load-more-btn {
      color: #108ee9;
      cursor: pointer;
      white-space: nowrap;
      display: flex;
      margin: 10px auto 0 auto;
      border-bottom: 1px solid transparent;
      transition: all 0.15s ease;
      width: fit-content;
    }
    .cp-load-more-btn.hidden {
      display: none;
    }
    .cp-load-more-btn:hover {
      color: #49a9ee;
      border-bottom: 1px solid #49a9ee;
    }
    .cp-spinner.spin:before {
      display: flex;
      content: url("data:image/svg+xml; utf8, %3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1024 1024' width='20' height='20'%3E%3Cpath fill='%23108ee9' d='M988 548c-19.9 0-36-16.1-36-36 0-59.4-11.6-117-34.6-171.3a440.45 440.45 0 0 0-94.3-139.9 437.71 437.71 0 0 0-139.9-94.3C629 83.6 571.4 72 512 72c-19.9 0-36-16.1-36-36s16.1-36 36-36c69.1 0 136.2 13.5 199.3 40.3C772.3 66 827 103 874 150c47 47 83.9 101.8 109.7 162.7 26.7 63.1 40.2 130.2 40.2 199.3.1 19.9-16 36-35.9 36z'/%3E%3C/svg%3E");
      width: 20px;
      height: 20px;
      margin: 10px auto 0 auto;
      animation: spin 1s infinite linear;
    }
    .cp-nothing-found {
      text-align: center;
      margin-top: 10px;
    }
    .cp-error {
      background: rgba(255, 0, 0, 0.2);
      padding: 10px;
      border-radius: 3px;
      margin-top: 10px;
    }
    .cp-error.hidden {
      display: none;
    }
    @keyframes spin {
      from {
        transform: rotate(0deg);
        transform-origin: 50% 50%;
      }
      to {
        transform: rotate(1turn);
      }
    }
  `;
  
  function getLocationInfo () {
    const info = location.pathname.split('/').filter(Boolean);
    staticUrl = info
      .splice(0, info.indexOf('static-resources') + 1)
      .join('/');
    const [storageId, ...rest] = info;
    const path = rest
      .filter(Boolean)
      .slice(0, rest.length - 1)
      .join('/');
    const file = rest.pop();
    return {
      staticUrl,
      storageId,
      path,
      file,
      origin: location.origin
    };
  }
  
  function handleSearch (event, searchQuery, loadMore = false) {
    event && event.stopPropagation();
    let scrollingParameters;
    const lastDocument = (documents || [])[documents.length - 1];
    if (loadMore && lastDocument) {
      scrollingParameters = {
        docId: lastDocument.elasticId,
        docScore: lastDocument.score,
        scrollingBackward: false
      };
    }
    const locationInfo = getLocationInfo();
    const storageFilter = {
      objectIdentifier: locationInfo.storageId,
      filterGlobs: [`/${locationInfo.path}/**`]
    };
    const options = {
      mode: 'cors',
      credentials: 'include',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json; charset=UTF-8;'
      },
      body: JSON.stringify({
        query: `*${searchQuery}*`,
        pageSize: PAGE_SIZE,
        highlight: true,
        aggregate: true,
        filterTypes: [
          "AZ_BLOB_FILE",
          "S3_FILE",
          "NFS_FILE",
          "GS_FILE"
        ],
        ...storageFilter,
        ...(scrollingParameters && {scrollingParameters})
      })
    };
    startPending();
    fetch(url, options)
      .then(response => response.json())
      .then(data => {
        if (data && data.status === 'ERROR') {
          return Promise.reject("Error loading search results")
        }
        const newDocuments = data && data.payload && data.payload.documents
          ? data.payload.documents
          : [];
        documents = loadMore
          ? [...documents, ...newDocuments]
          : newDocuments;
        renderSearchResults();
        stopPending();
      })
      .catch((error) => {
        stopPending();
        renderError(error);
      });
  }
  
  function showLoadMore () {
    const loadMore = container.querySelector('.cp-load-more-btn');
    loadMore.classList.remove('hidden');
  }
  
  function hideLoadMore () {
    const loadMore = container.querySelector('.cp-load-more-btn');
    loadMore.classList.add('hidden');
  }
  
  function startPending () {
    const loadMore = container.querySelector('.cp-load-more-btn');
    const searchInput = container.querySelector('.cp-search-input');
    const searchButton = container.querySelector('.cp-search-btn');
    const spinner = container.querySelector('.cp-spinner');
    const nothingFound = container.querySelector('.cp-nothing-found');
    nothingFound && nothingFound.remove();
    spinner.classList.add('spin');
    [loadMore, searchInput, searchButton].forEach(element => {
      element.setAttribute("disabled", "");
      element.classList.add("cp-pending");
    });
  }
  
  function stopPending () {
    const loadMore = container.querySelector('.cp-load-more-btn');
    const searchInput = container.querySelector('.cp-search-input');
    const searchButton = container.querySelector('.cp-search-btn');
    const spinner = container.querySelector('.cp-spinner');
    spinner.classList.remove('spin');
    [loadMore, searchInput, searchButton].forEach(element => {
      element.removeAttribute("disabled");
      element.classList.remove("cp-pending");
    });
  }
  
  function renderSearchResults () {
    const renderHighlight = (highlight) => {
      if (Array.isArray(highlight)) {
        return highlight[0]
          .replace(/<highlight>/gm, "<span class=\"cp-highlight\">")
          .replace(/<\/highlight>/gm, "</span>")
      }
      return highlight;
    };
    const getDocumentUrl = (document) => {
      if (document && document.path) {
        const {origin, staticUrl, storageId} = getLocationInfo();
        return `${origin}/${staticUrl}/${storageId}/${document.path}`;
      }
      return '#';
    };
    const renderDocument = (document) => {
      return (`
      <a href="${getDocumentUrl(document)}" class="cp-result-card">
        <div class="cp-result-card-title">${document.name}</div>
        <table class="cp-highlights">
          ${(document.highlights || []).map(highlight => (`
          <tr>
            <td class="cp-name">Found in ${highlight.fieldName}:</td>
            <td class="cp-matches">
              <p>...${renderHighlight(highlight.matches)}...</p>
            </td>
          </tr>`)).join('')}
        </table>
      </a>
    `)
    };
    clearSearchResults();
    const resultsContainer = container.querySelector('.cp-results-container');
    const results = document.createElement('div');
    results.classList.add('cp-results');
    let elements;
    if (documents && documents.length > 0) {
      elements = documents.map(renderDocument);
      resultsContainer.classList.add('with-content');
    } else {
      elements = ['<div class="cp-nothing-found">Nothing found.</div>'];
      resultsContainer.classList.remove('with-content');
    }
    results.innerHTML = elements.join('');
    resultsContainer.insertAdjacentElement('afterbegin', results);
    documents && documents.length >= PAGE_SIZE
      ? showLoadMore()
      : hideLoadMore();
  }
  
  function renderError (error) {
    const errorContainer = container.querySelector('.cp-error');
    if (error && errorContainer) {
      errorContainer.innerText = error;
      errorContainer.classList.remove('hidden');
    }
  }
  
  function clearError () {
    const errorContainer = container.querySelector('.cp-error');
    if (errorContainer) {
      errorContainer.innerText = '';
      errorContainer.classList.add('hidden');
    }
  }
  
  function clearSearchResults () {
    const resultsContainer = container.querySelector('.cp-results-container');
    const results = container.querySelector('.cp-results');
    resultsContainer.classList.remove('with-content');
    results && results.remove();
    clearError();
  }
  
  function clearSearchInput () {
    const searchInput = container.querySelector('.cp-search-input');
    searchInput.value = '';
  }

  function openSearchOverlay () {
    const searchInput = container.querySelector('.cp-search-input');
    container.classList.add('expanded');
    searchInput.focus();
  }

  function closeSearchOverlay () {
    clearSearchResults();
    clearSearchInput();
    hideLoadMore();
    container.classList.remove('expanded');
  }
  
  function initializeSearch () {
    const initLayout = () => {
      container = document.createElement('div');
      const content = `
        <div class="cp-search-controls">
          <input
            class="cp-search-input"
            placeholder="Search"
          />
          <button class="cp-search-btn" disabled>
            SEARCH
          </button>
          <span class="cp-expand-btn"></span>
        </div>
        <span class="cp-spinner"></span>
        <span class="cp-error hidden"></span>
        <div class="cp-results-container">
          <a class="cp-load-more-btn hidden">Load more</a>
        </div>
      `;
      container.innerHTML = content;
    };
    const initStyles = () => {
      container.classList.add('cp-container');
      const styleSheet = document.createElement('style');
      styleSheet.appendChild(document.createTextNode(CSS_TEXT));
      container.insertAdjacentElement('afterbegin', styleSheet);
    };
    const initListeners = () => {
      const searchInput = container.querySelector('.cp-search-input');
      const searchButton = container.querySelector('.cp-search-btn');
      const expandButton = container.querySelector('.cp-expand-btn');
      const loadMoreButton = container.querySelector('.cp-load-more-btn');
      window.addEventListener('keydown', (event) => {
        if (event && event.key === 'Escape') {
          closeSearchOverlay();
        }
      });
      searchButton.addEventListener('click', (event) => {
        handleSearch(event, searchInput.value)
      });
      searchInput.addEventListener('keydown', (event) => {
        if (event && event.key === 'Enter') {
          handleSearch(event, searchInput.value);
        }
      });
      searchInput.addEventListener('input', event => {
        const searchQuery = (searchInput.value || "").trim();
        searchQuery.length > 0
          ? searchButton.removeAttribute("disabled")
          : searchButton.setAttribute("disabled", "");
      });
      expandButton.addEventListener('click', () => {
        container.classList.contains('expanded')
          ? closeSearchOverlay()
          : openSearchOverlay()
      });
      loadMoreButton.addEventListener('click', (event) => {
        event && event.preventDefault();
        if (!event.currentTarget.classList.contains('cp-pending')) {
          handleSearch(event, searchInput.value, true);
        }
      });
    };
    initLayout();
    initStyles();
    initListeners();
    document.body.appendChild(container);
  }

  initializeSearch();
});