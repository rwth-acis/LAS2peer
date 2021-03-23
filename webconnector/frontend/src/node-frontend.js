/**
 * @license
 * Copyright (c) 2016 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE.txt
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS.txt
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS.txt
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS.txt
 */

import { PolymerElement, html } from '@polymer/polymer/polymer-element.js';
import { setPassiveTouchGestures, setRootPath } from '@polymer/polymer/lib/utils/settings.js';
import '@polymer/app-layout/app-drawer/app-drawer.js';
import '@polymer/app-layout/app-drawer-layout/app-drawer-layout.js';
import '@polymer/app-layout/app-header/app-header.js';
import '@polymer/app-layout/app-header-layout/app-header-layout.js';
import '@polymer/app-layout/app-scroll-effects/app-scroll-effects.js';
import '@polymer/app-layout/app-toolbar/app-toolbar.js';
import '@polymer/app-route/app-location.js';
import '@polymer/app-route/app-route.js';
import '@polymer/iron-ajax/iron-ajax.js';
import '@polymer/iron-pages/iron-pages.js';
import '@polymer/iron-selector/iron-selector.js';
import '@polymer/paper-button/paper-button.js';
import '@polymer/paper-card/paper-card.js';
import '@polymer/paper-dialog/paper-dialog.js';
import '@polymer/paper-icon-button/paper-icon-button.js';
import '@polymer/paper-input/paper-input.js';
import 'las2peer-frontend-statusbar/las2peer-frontend-statusbar.js';

// Gesture events like tap and track generated from touch will not be
// preventable, allowing for better scrolling performance.
setPassiveTouchGestures(true);

// Set Polymer's root path to the same value we passed to our service worker
// in `index.html`.
setRootPath(NodeFrontendGlobals.rootPath);

class NodeFrontend extends PolymerElement {
  static get template() {
    return html`
      <iron-ajax id="ajaxLogin"
                 url$="[[apiEndpoint]]/auth/login"
                 handle-as="json"
                 on-response="_handleLoginResponse"
                 on-error="_handleError"
                 loading="{{_submittingLogin}}"></iron-ajax>
      <iron-ajax id="ajaxDestroySession"
                 method="POST"
                 url$="[[apiEndpoint]]/auth/logout"
                 handle-as="json"
                 on-response="_handleLogoutResponse"
                 on-error="_handleError"
                 loading="{{_submittingLogout}}"></iron-ajax>
      <iron-ajax id="ajaxValidateSession"
                 url$="[[apiEndpoint]]/auth/validate"
                 handle-as="json"
                 on-response="_handleValidateResponse"
                 on-error="_handleError"
                 loading="{{_submittingLogin}}"></iron-ajax>

      <iron-ajax id="ajaxCheckETH"
                 url$="[[apiEndpoint]]/check-eth"
                 on-response="_handleCheckETHResponse"
                 on-error="_handleCheckETHError"
                 loading="{{_checking}}"></iron-ajax>
      <iron-ajax id="ajaxCheckFileService"
                 url$="[[hostRoot]]fileservice/index.html"
                 on-response="_handleCheckFSResponse"
                 on-error="_handleCheckFSError"
                 handle-as="document"
                 loading="{{_checking}}"></iron-ajax>
      <iron-ajax id="ajaxCheckContactService"
                 url$="[[hostRoot]]contactservice/groups"
                 on-response="_handleCheckCSResponse"
                 on-error="_handleCheckCSError"
                 handle-as="json"
                 params='{}'
                 loading="{{_checking}}"></iron-ajax>


      <style>
        :host {
          --app-primary-color: #4285f4;
          --app-secondary-color: black;

          display: block;
        }

        app-drawer-layout:not([narrow]) [drawer-toggle] {
          display: none;
        }

        .app-header {
          color: #fff;
        }
        
        las2peer-frontend-statusbar{
          --statusbar-background: var(--app-primary-color);
          --user-widget-button-background: white;
        }

        app-header paper-icon-button {
          --paper-icon-button-ink-color: white;
        }

        .drawer-list {
          margin: 0 20px;
        }

        .drawer-list a {
          display: block;
          padding: 0 16px;
          text-decoration: none;
          color: var(--app-secondary-color);
          line-height: 40px;
        }

        .drawer-list a.iron-selected {
          color: black;
          font-weight: bold;
        }

        .cursorwrapper:hover {
          cursor:pointer;
        }

        paper-toast .error {
          --paper-toast-background-color: rgba(255,0,0,0.7);
          --paper-toast-color: white;
        }
      </style>

      <app-location route="{{route}}" url-space-regex="^[[rootPath]]"></app-location>

      <app-route route="{{route}}" pattern="[[rootPath]]:page" data="{{routeData}}" tail="{{subroute}}"></app-route>

      <app-drawer-layout fullbleed="" narrow="{{narrow}}">
        <!-- Drawer content -->
        <app-drawer id="drawer" slot="drawer" swipe-open="[[narrow]]">
          <app-toolbar>
            <div style="">
              Menu
            </div>

            
            <div style="margin-left: auto;order: 2;display: flex;align-content: center;align-items: center;">
              <paper-spinner active="[[_checking]]" style="width: 18px; height: 18px;"></paper-spinner>
              </span>
              <!-- Is running blockchain? -->
              <template is="dom-if" if="[[_isEthNode]]">
                <iron-icon icon="fingerprint" 
                  title="Node is running on Ethereum" 
                  style="width: 18px; height: 18px; color: green;"></iron-icon> 
              </template>
              <template is="dom-if" if="[[!_isEthNode]]">
                <iron-icon icon="fingerprint" 
                  title="Node is NOT running on Ethereum" 
                  style="width: 18px; height: 18px; color: red;"></iron-icon> 
              </template>

              <!-- Is FileService running? -->
              <template is="dom-if" if="[[_isFileServiceRunning]]">
                <iron-icon icon="description" 
                  title="FileService is Running" 
                  style="width: 18px; height: 18px; color: green;"></iron-icon> 
              </template>
              <template is="dom-if" if="[[!_isFileServiceRunning]]">
                <iron-icon icon="description" 
                  title="FileService is NOT Running" 
                  style="width: 18px; height: 18px; color: red;"></iron-icon> 
              </template
              
              <!-- Is ContactService running? -->
              <template is="dom-if" if="[[_isContactServiceRunning]]">
                <iron-icon icon="perm-contact-calendar" 
                  title="ContactService is Running" 
                  style="width: 18px; height: 18px; color: green;"></iron-icon> 
              </template>
              <template is="dom-if" if="[[!_isContactServiceRunning]]">
                <iron-icon icon="perm-contact-calendar" 
                  title="ContactService is NOT Running OR user is not logged in." 
                  style="width: 18px; height: 18px; color: red;"></iron-icon> 
              </template>

              <!-- Is UserInformationService running? 
              <template is="dom-if" if="[[_isUserInformationServiceRunning]]">
                <iron-icon icon="supervisor-account" 
                  title="UserInformationService is Running" 
                  style="width: 18px; height: 18px; color: green;"></iron-icon> 
              </template>
              <template is="dom-if" if="[[!_isUserInformationServiceRunning]]">
                <iron-icon icon="supervisor-account" 
                  title="UserInformationService is NOT Running" 
                  style="width: 18px; height: 18px; color: red;"></iron-icon> 
              </template>
              -->
              <paper-icon-button icon="refresh" title="Refresh Status" on-click="checkStatusWrapper" disabled="[[_checking]]"></paper-icon-button>
            </div>
          </app-toolbar>

          <iron-selector selected="[[page]]" attr-for-selected="name" class="drawer-list" role="navigation">
            <a name="welcome" href="[[rootPath]]welcome">Welcome</a>
            <a name="status" href="[[rootPath]]status">Status</a>
            <a name="view-services" href="[[rootPath]]view-services">View Services</a>
            <a name="publish-service" href="[[rootPath]]publish-service">Publish Service</a>
            <template is="dom-if" if="[[_isEthAgent]]">
              <a name="eth-tools" href="[[rootPath]]eth-tools">Blockchain and Reputation</a>
            </template>
            <a name="agent-tools" href="[[rootPath]]agent-tools">Agent Tools</a>
          </iron-selector>
        </app-drawer>

        <!-- Main content -->
        <div class="app-header">
          <template is="dom-if" if="[[_submittingLogin]]">
            <paper-spinner style="position: absolute;float: right;right: 170px;top: 25px;z-index: 1;" active="[[_submittingLogin]]"></paper-spinner>
          </template>
            <las2peer-frontend-statusbar id="statusbar"
              base-url="[[hostUrl]]" service="las2peer Node Front-End"
              oidcclientid="bb767191-32ed-44ed-b007-7fa0858133b6"
              oidcpopupsigninurl$="[[_loadUrl]]"
              oidcpopupsignouturl$="[[_loadUrl]]"
              oidcsilentsigninurl$="[[_loadUrl]]"
              loginoidctoken$="[[_oidcUser.access_token]]"
              loginoidcprovider="https://api.learning-layers.eu/o/oauth2"
              subtitle="v@LAS2PEER_VERSION@"
            ></las2peer-frontend-statusbar>

          <iron-pages selected="[[page]]" attr-for-selected="name" fallback-selection="view404" role="main">
            <welcome-view name="welcome" api-endpoint="[[apiEndpoint]]" agent-id="[[_agentId]]" error="{{_error}}"></welcome-view>
            <status-view name="status" api-endpoint="[[apiEndpoint]]" agent-id="[[_agentId]]" error="{{_error}}"></status-view>
            <services-view name="view-services" api-endpoint="[[apiEndpoint]]" agent-id="[[_agentId]]" error="{{_error}}"></services-view>
            <service-publish-view name="publish-service" api-endpoint="[[apiEndpoint]]" agent-id="[[_agentId]]" error="{{_error}}"></service-publish-view>
            <agents-view name="agent-tools" api-endpoint="[[apiEndpoint]]" agent-id="[[_agentId]]" error="{{_error}}"></agents-view>
            <eth-view name="eth-tools" api-endpoint="[[apiEndpoint]]" agent-id="[[_agentId]]" error="{{_error}}"></eth-view>
            <my-view404 name="view404"></my-view404>
          </iron-pages>
        </div>
        <!-- modal dialogs -->
        <paper-dialog id="loginDialog" modal="[[_submittingLogin]]">
          <h2>Login</h2>

          <div hidden$="[[!_oidcUser]]" style="text-align: center">
            <paper-card heading$="[[_oidcUser.profile.name]]">
              <div class="card-content">
                <div>Enter your las2peer password below to sign in.</div>
                <div style="margin-top: 1em">If this is your first visit, enter a new password.<br/>You will be registered automatically.</div>
              </div>
              <div class="card-actions">
                <paper-button id="oidcChangeUserButton">Change User</paper-button>
              </div>
            </paper-card>
          </div>

            <openidconnect-popup-signin-callback></openidconnect-popup-signin-callback>
            <openidconnect-popup-signout-callback></openidconnect-popup-signout-callback>
            <openidconnect-signin-silent-callback></openidconnect-signin-silent-callback>

            <div style="margin-top: 1em">Or enter your login name:</div>
          </div>

          <!-- This form is not used right now since the login is completely based on the OIDC credentials -->
          <form is="iron-form" id="loginForm" on-keypress="_keyPressedLogin" style="margin-top: 0; margin-bottom: 1em">
            <paper-input hidden$="[[toBool(_oidcUser)]]" label="email or username" id="userIdField" disabled="[[_submittingLogin]]" value="" autofocus></paper-input>
            <paper-input label="password" id="passwordField" disabled="[[_submittingLogin]]" value="" type="password">
              <paper-icon-button id="loginButton" icon="send" slot="suffix"></paper-icon-button>
            </paper-input>
            <!-- hidden button is triggered via paper button above -->
            <input type="submit" id="loginSubmitButton" style="display: none" />
          </form>
          <dom-if if="[[_submittingLogin]]">
            <template>
            <paper-spinner style="left: 38%; position: absolute; z-index: 10" active="[[_submittingLogin]]"></paper-spinner>
            </template>
          </dom-if>

          <div hidden$="[[toBool(_oidcUser)]]">To register, use the <a dialog-dismiss name="view-agents" href="[[rootPath]]agent-tools">Agents</a> tab.</div>
        </paper-dialog>

      </app-drawer-layout>



      <paper-toast id="las2peerErrorDialog" horizontal-align="left" class="error">
        <h2 id="las2peerErrorDialogTitle">Error</h2>
        <div id="las2peerErrorDialogMessage"></div>
      </paper-toast>
    `;
  }

  static get properties() {
    return {
      page: { type: String, reflectToAttribute: true, observer: '_pageChanged' },
      routeData: Object,
      subroute: Object,
      hostUrl: {type: String, value: null},
      apiEndpoint: { type: String, value: '/las2peer' },
      hostRoot: { type: String, value: document.URL.split(":")[0] + "://" + window.location.host + '/' },
      _agentId: { type: String, value: '' },
      _submittingLogin: { type: Boolean, value: false },
      _error: { type: Object, observer: '_errorChanged' },
      _oidcUser: { type: Object, value: null},
      _isEthAgent: { type: Boolean, value: false },
      _isLoggedIn: { type: Boolean, value: false },
      _checking: { type: Boolean, value: false },
      _isEthNode: { type: Boolean, value: false },
      _isFileServiceRunning: { type: Boolean, value: false },
      _isContactServiceRunning: { type: Boolean, value: false },
      _isUserInformationServiceRunning: { type: Boolean, value: false },
    };
  }

  static get observers() {
    return [
      '_routePageChanged(routeData.page)'
    ];
  }

  toBool(prop) {
    return !!prop;
  }

  _routePageChanged(page) {
     // Show the corresponding page according to the route.
     //
     // If no page was found in the route data, page will be an empty string.
     // Show 'view1' in that case. And if the page doesn't exist, show 'view404'.
    if (!page) {
      this.page = 'welcome';
    } else if (['welcome', 'status', 'view-services', 'publish-service', 'agent-tools', 'eth-tools'].indexOf(page) !== -1) {
      this.page = page;
    } else {
      this.page = 'view404';
    }

    // Close a non-persistent drawer when the page & route are changed.
    if (!this.$.drawer.persistent) {
      this.$.drawer.close();
    }
  }

  _pageChanged(page) {
    // Import the page component on demand.
    //
    // Note: `polymer build` doesn't like string concatenation in the import
    // statement, so break it up.
    switch (page) {
      case 'welcome':
        import('./view-welcome.js');
        break;
      case 'status':
        import('./view-status.js');
        break;
      case 'view-services':
        import('./view-services.js');
        break;
      case 'publish-service':
        import('./view-publish-service.js');
        break;
      case 'agent-tools':
        import('./view-agents.js');
        break;
      case 'eth-tools':
        import('./view-eth.js');
        break;
      case 'view404':
        import('./my-view404.js');
        break;
    }
  }

  ready() {
    super.ready();
    let rootThis = this;
    window.rootThis = rootThis;

    this._loadUrl = document.URL; // there's definitely better ways to do this, but I have no idea

    let scheme = this._loadUrl.split(":")[0] + "://";
    this.hostUrl = scheme + window.location.host;

    this.$.ajaxValidateSession.generateRequest(); // validate old session

    this.$.statusbar.addEventListener('signed-in', function(event) { rootThis.storeOidcUser(event.detail); });

    this.$.statusbar.addEventListener('signed-out', function() { rootThis._oidcUser = null; rootThis.destroySession(); });

    // deprecated code supporting login with las2peer credentials
    // trigger the hidden, real submit button
    this.$.loginButton.addEventListener('click', function() { rootThis.$.loginSubmitButton.click(); });

    this.$.loginForm.addEventListener('submit', function(event) { event.preventDefault(); rootThis.sendLogin(); });

    this.$.oidcChangeUserButton.addEventListener('click', function() { rootThis.$.statusbar.shadowRoot.querySelector("#oidcButton")._handleClick(); });

    this.checkStatus();
    //window.setInterval(function() { rootThis.checkStatus(); }, 50000);
  }

  oidcTokenStillValid(userObject) {
    // it's possible that the OIDC element triggers a logout when the token
    // expires, maybe. I don't know. TODO: check if this needs handling
    return (userObject.expires_at * 1000) > Date.now();
  }

  destroySession() {
    this.$.ajaxDestroySession.generateRequest();
  }

  storeOidcUser(userObject) {
    if (userObject.token_type !== "Bearer") throw "unexpected OIDC token type, fix me";
    this._oidcUser = userObject;
    this.sendLogin();
  }

  sendLogin() {
    // the code for detecting the login method is deprecated
    // the only credentials that are used are the OIDC access token and OIDC sub
    const PREFIX_USER_NAME = "USER_NAME-";
    const PREFIX_USER_MAIL = "USER_MAIL-";
    const PREFIX_OIDC_SUB = "OIDC_SUB-";

    let credentials = {
      loginNameOrEmail: this.$.userIdField.value,
      oidcSub: ((this._oidcUser || {}).profile || {}).sub,
      password: this.$.passwordField.value
    };

    // yeah, no, this isn't great, but see LAS-452
    let looksLikeEmail = (credentials.loginNameOrEmail || "").indexOf('@') > -1;

    let prefixedIdentifier;
    if (looksLikeEmail) {
      prefixedIdentifier = PREFIX_USER_MAIL + credentials.loginNameOrEmail;
    } else if ((credentials.loginNameOrEmail || "").length > 0) {
      prefixedIdentifier = PREFIX_USER_NAME + credentials.loginNameOrEmail;
    } else if ((credentials.oidcSub || "").length > 0) {
      prefixedIdentifier = PREFIX_OIDC_SUB + credentials.oidcSub;
      console.log(credentials.oidcSub);
    } else {
      this._handleError(credentials, "Malformed credentials", "Entered credentials were incomplete. (Empty login name?)")
      return;
    }

    this._submittingLogin = true;
    let req = this.$.ajaxLogin;
    req.headers = { Authorization: 'Basic ' + btoa(prefixedIdentifier + ':' + credentials.oidcSub) };
    if (((this._oidcUser || {}).access_token || "").length > 0) {
      req.headers['access-token'] = this._oidcUser.access_token;
    }
    req.generateRequest();
  }

  _keyPressedLogin(event) {
    if (event.which == 13 || event.keyCode == 13) {
      this.$.loginSubmitButton.click();
      return false;
    }
    return true;
  }

  _handleLoginResponse(event) {
    this._submittingLogin = false;
    console.log("login response: ", event);
    let resp = event.detail.response;
    if (resp && resp.hasOwnProperty('agentid')) {
      this._agentId = resp.agentid;
      console.log("login successful. agent ID: " + this._agentId);
      this.$.loginDialog.close();
      this.$.userIdField.value = '';
      this.$.passwordField.value = '';
      this.$.statusbar._appendWidget();
      if ( resp.hasOwnProperty('ethaddress') )
      {
        console.log("ethereum agent detected: " + resp.ethaddress);
        this._isEthAgent = true;
      }
    } else {
      this._handleError(event, "Bad response", "Login returned no agent ID")
    }
  }

  _handleLogoutResponse() {
    this._agentId = '';
    this.$.statusbar._appendWidget();
  }

  _handleValidateResponse(event) {
    console.log("validate response: ", event);
    let resp = event.detail.response;
    if (!resp || resp.agentid === undefined || resp.agentid === '') {
      this._handleLogoutResponse();
    } else {
      this._agentId = resp.agentid;
    }
  }

  checkStatusWrapper()
  {
    window.rootThis.checkStatus();
  }

  checkStatus()
  {
    this.$.ajaxCheckContactService.headers = this.$.ajaxLogin.headers;
    this.$.ajaxCheckETH.generateRequest();
    this.$.ajaxCheckFileService.generateRequest();
    this.$.ajaxCheckContactService.generateRequest();
  }

  _handleCheckETHResponse(event) {
    this._isEthNode = true;
  }
  _handleCheckETHError(event) {
    this._isEthNode = false;
  }
  _handleCheckFSResponse(event) {
    this._isFileServiceRunning = true;
  }
  _handleCheckFSError(event) {
    this._isFileServiceRunning = false;
  }
  _handleCheckCSResponse(event) {
    this._isContactServiceRunning = true;
  }
  _handleCheckCSError(event) {
    this._isContactServiceRunning = false;
  }

  // iron-ajax error event for some reason passes two arguments
  // that can be confusing, but it's not a problem
  _handleError(object, title, message) {
    if (!title || !message) {
      // try to get details of known possible errors
      let maybeDetail = (object || {}).detail;
      let maybeError = (maybeDetail || {}).error;
      let maybeRequest = (maybeDetail || {}).request;
      let maybeXhr = (maybeRequest || {}).xhr;

      // TODO: this is for from perfect. all fields should be checked before usage
      if ((maybeXhr || {}).readyState === 4 && (maybeXhr || {}).status === 0) { // network issues
        title = 'Network Connection Error';
        message = 'Could not connect to: ' + (maybeRequest || {}).url;
      } else if ((maybeXhr || {}).status && (maybeXhr.response || {}).msg) {
        title = maybeXhr.status + " - " + maybeXhr.statusText;
        message = maybeXhr.response.msg;
      } else if ((maybeXhr || {}).status && (maybeError || {}).message) {
        title = maybeXhr.status + " - " + maybeXhr.statusText;
        message = maybeError.message;
      } else {
        title = "Unknown error";
        message = "Could not determine type of error, check manually in console"
      }

      if ( ((maybeRequest || {}).__data || {}).status == 404 )
      {
        title = "Service unreachable";
        if ( (maybeRequest || {}).url.includes("/contactservice/") )
        {
          message = "ContactService not reachable. Ensure it is running.";
          if ( !this._isContactServiceRunning )
            return;
          this._isContactServiceRunning = false;
        }
        if ( (maybeRequest || {}).url.includes("/fileservice/") )
        {
          message = "FileService not reachable. Ensure it is running.";
          if ( !this._isFileServiceRunning )
            return;
          this._isFileServiceRunning = false;
        }
      }
      if ( message.includes("Node does not use registry") ) {
        this._isEthNode = false;
      }
      if ( message.includes("Couldn't query mobsos success model for group") )
      {
        this._error = { title: "Couldn't query MobSOS success model", msg: "Either the service is not running or the success model is not setup." };
        return;
      }
    }
    this._error = { title: title, msg: message, obj: object };
  }

  _errorChanged(error) {
    this.$.las2peerErrorDialog.close(); // otherwise the dialog is rendered in wrong place
    this.$.las2peerErrorDialogTitle.innerHTML = error.title;
    this.$.las2peerErrorDialogMessage.innerHTML = error.msg;
    this.$.las2peerErrorDialog.open();
  }
}

window.customElements.define('node-frontend', NodeFrontend);
