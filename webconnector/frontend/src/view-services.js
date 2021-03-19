/**
 * @license
 * Copyright (c) 2016 The Polymer Project Authors. All rights reserved.
 * This code may only be used under the BSD style license found at http://polymer.github.io/LICENSE.txt
 * The complete set of authors may be found at http://polymer.github.io/AUTHORS.txt
 * The complete set of contributors may be found at http://polymer.github.io/CONTRIBUTORS.txt
 * Code distributed by Google as part of the polymer project is also
 * subject to an additional IP rights grant found at http://polymer.github.io/PATENTS.txt
 */

import { PolymerElement, html } from "@polymer/polymer/polymer-element.js";
import "@polymer/iron-ajax/iron-ajax.js";
import "@polymer/iron-form/iron-form.js";
import "@polymer/iron-icon/iron-icon.js";
import "@polymer/iron-icons/iron-icons.js";
import "@polymer/iron-icons/device-icons.js";
import "@polymer/iron-icons/hardware-icons.js";
import "@polymer/paper-card/paper-card.js";
import "@polymer/paper-button/paper-button.js";
import "@polymer/paper-input/paper-input.js";
import "@polymer/paper-tooltip/paper-tooltip.js";
import "./custom-star-rating.js";
import "./shared-styles.js";

class ServicesView extends PolymerElement {
  static get template() {
    return html`
      <iron-ajax id="ajaxNodeId"
                 auto
                 url$="[[apiEndpoint]]/services/node-id"
                 handleAs="json"
                 last-response="{{_nodeId}}"
                 on-error="_handleError"
                 debounce-duration="300"></iron-ajax>
      <iron-ajax id="ajaxServiceData"
                 auto
                 url$="[[apiEndpoint]]/services/services"
                 handle-as="json"
                 last-response="{{_services}}"
                 on-error="_handleError"
                 debounce-duration="300"></iron-ajax>
      <iron-ajax id="ajaxCommunityTags"
                 auto
                 url$="[[apiEndpoint]]/services/registry/tags"
                 handleAs="json"
                 last-response="{{_communityTags}}"
                 on-error="_handleError"
                 debounce-duration="300"></iron-ajax>
      <iron-ajax id="ajaxStartService"
                 method="POST"
                 url$="[[apiEndpoint]]/services/start"
                 handle-as="text"
                 on-error="_handleError"
                 on-response="_handleServiceStart"
                 loading="{{_working}}"></iron-ajax>
      <iron-ajax id="ajaxStopService"
                 method="POST"
                 url$="[[apiEndpoint]]/services/stop"
                 handle-as="text"
                 on-error="_handleError"
                 loading="{{_working}}"></iron-ajax>

      <style include="shared-styles">
        :host {
          display: block;

          padding: 10px;
        }
        .service .nodeId, .service .nodeAdmin, .service .nodeAdminRating, .service .time {
          display: inline-block;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .service .nodeId, .service .nodeAdmin, .service .nodeAdminRating 
        {
          margin-right: 1em;
        }
        .service .nodeId {
          width: 6.5em;
        }
        .service .nodeAdmin {
          min-width: 8em;
        }
        .service .nodeAdminRating {
          min-width: 120px;
        }
        .release-deployments-paper {
          width: 100%;
          display: flex;
          justify-content: space-between;
          flex-flow: row;
          align-items: center;
          align-content: center;
          padding: 2em;
        }
        .open-release-application {
          align-items: center;
          align-content: center;
          color: rgb(240, 248, 255);
          background: rgb(30, 144, 255);
        }
        .stop-release-application {
          align-items: center;
          align-content: center;
          color: rgb(240, 248, 255);
          background: red;
        }
        .deploy-instance {
          align-items: center;
          display: flex;
          align-content: center;
          color: rgb(240, 248, 255);
          background: rgb(30, 144, 255);
          max-height: 30px;
        }
      </style>
      <custom-style>
        <style is="custom-style">
          paper-tooltip.large {
            --paper-tooltip: {
              font-size: medium;
            }
          }
        </style>
      </custom-style>

      <div class="card">
        <div style="float: right; width: 15em; margin-top: -8px">
          <paper-input label="search" id="filterField" on-value-changed="_triggerFilter" value=""></paper-input>
        </div>
        <h1>Services in this Network</h1>

        <p hidden$="[[_toBool(_services)]]">
          There are no services published in this network.<br/>
          Feel free to use the <a href="[[rootPath]]publish-service">Publish Service</a> tab.
        </p>

        <template is="dom-if" if="[[window.rootThis._isEthNode]]">
          <p>
            No Blockchain instance detected. <em>Fallback to manual service starting procedure.</em> <br />
            Service must be uploaded to Pastry storage via "Publish service" first. <br />
            Enter full namespace + service name / class name followed by '@' and the version number, e.g.: <br />
            <pre>i5.las2peer.services.fileService.FileService@2.2.5</pre>
          </p>
          <paper-input label="ServiceFullName@Version" id="serviceString" disabled="[[!window.rootThis._isEthNode]]" value$="[[_serviceString]]"></paper-input> <br />
          <paper-button on-click="_handleStartButtonNoChain">Start sevice locally</paper-button>
          <paper-spinner style="padding: 0.7em;float: right;" active="[[_working]]"></paper-spinner>

        </template>

        <template id="serviceList" is="dom-repeat" items="[[_services]]" as="service" sort="_sort" filter="_filter">
          <template is="dom-repeat" items="[[_getLatestAsArray(service.releases)]]" as="release">
            <!-- we actually just want a single item here: the latest release. but I don't know how to do that without abusing repeat like this -->
            <template is="dom-if" if="[[_clusterTypeAvailable(release)]]">
              <paper-card heading$="[[release.supplement.name]]" style="width: 100%;margin-bottom: 1em" class="service">
                <div class="card-content" style="padding-top: 0px">
                  <div style="margin-bottom: 8px">
                    <span class="package"><iron-icon icon="icons:cloud" title="Part of package"></iron-icon>[[service.name]]</span>
                  </div>
                  <div>
                    Author: <span class="author">[[service.authorName]]</span> 
                    <template is="dom-if" if="[[service.authorReputation]]">
                      <custom-star-rating value="[[service.authorReputation]]" readonly single></custom-star-rating>
                    </template>
                    <template is="dom-if" if="[[!service.authorReputation]]">
                      <custom-star-rating disable-rating readonly single></custom-star-rating>
                    </template>
                  </div>
                  <div>
                    Latest version: <span class="version">[[release.version]]</span>
                    published <span class="timestamp">[[_toHumanDate(release.publicationEpochSeconds)]]</span>
                  </div>
                  <p class="description">[[release.supplement.description]]</p>
                  <details>
                    <summary>
                      <div style="display: inline-block; vertical-align: top">
                        Service has [[_getNumberOfReleases(service)]] releases<br/>
                      </div>
                    </summary>
                    <template is="dom-repeat" items="[[_toArray(service.releases)]]" as="version">
                      <div style="display: flex; flex-direction: row;align-items: center;align-content: center; padding: 10px;">
                        <details style="flex-grow: 4; ">
                          <summary class="summary">
                            Deployments of release version: [[version.name]]
                          </summary>
                          <template is="dom-repeat" items="[[_getDeploymentsAsArray(service.releases, version.name)]]" as="instance">
                            <div>
                              <paper-card class="release-deployments-paper">
                                <div class="deployment-info">
                                  [[instance.clusterName]] Time:
                                  [[instance.time]]
                                </div>
                                <div>
                                  <paper-button class="open-release-application">Open app</paper-button>
                                  <paper-button class="stop-release-application">Stop deployment</paper-button>
                                </div>
                              </paper-card>
                            </div>
                          </template>
                        </details>
                        <div style="padding-right: 55px;">
                          <paper-button class="deploy-instance" data-args$='[[version]]' on-click="test">Deploy instance</paper-button>
                        </div>
                      </div>
                    </template>
                  </details>
                  <div id="[[service.name]]" style="display: none">
                    <paper-card class="deploy-paper" style="width:100%; padding:40px">
                        <div style="display:flex; flex-direction: column; flex-grow: 4;">
                          <div style="display:flex; flex-direction: column;">
                            <div style="display:flex; flex-direction: row;justify-content: space-between;align-items: center;align-content: center;">
                              <div>Name</div>
                                <paper-icon-button
                                  icon="close"
                                ></paper-icon-button>
                            </div>
                              <span class="textbox" style="display:flex; flex-direction: row;">
                                <paper-input
                                  type="text"
                                  id="release-name-input"
                                  value=[[selectedRelease.value.supplement.name]]
                                  readonly
                                ></paper-input>
                                <paper-input
                                  id="name-input"
                                  type="text"
                                ></paper-input>
                              </span>
                          </div>
                          <div style="display:flex; flex-direction: column;">
                              URL
                              <span
                                class="textbox"
                                style="display:flex; flex-direction: row;"
                              >
                                <paper-input
                                  id="url-input"
                                  type="text"
                                ></paper-input>
                              </span>
                            </div>
                        </div>
                        <paper-card>
                          <paper-button
                            id="deployment-button"
                            class="paper-button-blue"
                          >
                            Deploy own Release
                          </paper-button>
                        </paper-card>
                    </paper-card>
                  </div>

                </div>
              </paper-card>
            </template>
            <template is="dom-if" if="[[!_clusterTypeAvailable(release)]]">
              <paper-card heading$="[[release.supplement.name]]" style="width: 100%;margin-bottom: 1em" class="service">
                <div class="card-content" style="padding-top: 0px">
                  <div style="margin-bottom: 8px">
                    <span class="package"><iron-icon icon="icons:archive" title="Part of package"></iron-icon>[[service.name]]</span>
                  </div>
                  <div>
                    Author: <span class="author">[[service.authorName]]</span> 
                    <template is="dom-if" if="[[service.authorReputation]]">
                      <custom-star-rating value="[[service.authorReputation]]" readonly single></custom-star-rating>
                    </template>
                    <template is="dom-if" if="[[!service.authorReputation]]">
                      <custom-star-rating disable-rating readonly single></custom-star-rating>
                    </template>
                  </div>
                  <div>
                    Latest version: <span class="version">[[release.version]]</span>
                    published <span class="timestamp">[[_toHumanDate(release.publicationEpochSeconds)]]</span>
                    <span class="history">
                      <iron-icon icon="icons:info" title="Release history"></iron-icon>
                      <paper-tooltip position="right" class="large">
                        Release History<br/>
                        <ul>
                          <template is="dom-repeat" items="[[_toArray(service.releases)]]" as="version">
                            <li>[[version.name]] at [[_toHumanDate(version.value.publicationEpochSeconds)]]</li>
                          </template>
                        </ul>
                      </paper-tooltip>
                    </span>
                  </div>
                  <p class="description">[[release.supplement.description]]</p>
                  <details>
                    <summary>
                      <div style="display: inline-block; vertical-align: top">
                        Service consists of [[_count(release.supplement.class)]] microservice[[_pluralS(release.supplement.class)]]<br/>
                        [[_countRunningLocally(release)]] running locally on this node, [[_countInstancesRunningRemoteOnly(release)]] running remotely in network
                        <div hidden="[[!_fullyAvailableLocally(release)]]">
                          Service available locally, authenticity verified
                          <iron-icon icon="hardware:security" title="Running locally"></iron-icon>
                        </div>
                        <div hidden="[[_fullyAvailableLocally(release)]]">
                          <div hidden="[[!_fullyAvailableAnywhere(release)]]">
                            Service available remotely on other nodes
                            <iron-icon icon="icons:cloud" title="Running on network nodes"></iron-icon>
                          </div>
                        </div>
                        <div hidden="[[_fullyAvailableAnywhere(release)]]">
                          Service not available
                        </div>
                        <!--
                        [[_countRunningLocally(release)]] of [[_count(release.supplement.class)]] Service classes running on this node
                        <iron-icon icon="hardware:security" title="Running locally"></iron-icon><br/>
                        <span hidden$="[[_fullyAvailableLocally(release)]]">
                        [[_countRunningRemoteOnly(release)]] of [[_countMissingLocally(release)]] running remotely in network
                        <iron-icon icon="icons:cloud" title="Running on network nodes"></iron-icon>
                        </span>
                        -->
                      </div>
                    </summary>
                    <ul style="list-style: none"><!-- TODO: this could/should actually be an HTML table, for once -->
                        <li>
                          <div style="display: inline-block; vertical-align: top; width: 17em; overflow: hidden">
                            <strong>Microservice</strong>
                          </div>
                          <ul style="display: inline-block; list-style: none; padding-left: 0">
                            <li style="margin-left: 0">
                              <span class="nodeId"><iron-icon icon="hardware:device-hub" title="Running on Node"></iron-icon> <strong>Node ID</strong></span>
                              <span class="nodeAdmin"><iron-icon icon="account-circle" title="Service Hoster"></iron-icon> <strong>Service Hoster</strong></span>
                              <span class="nodeAdminRating"><iron-icon icon="face" title="Hoster Rating"></iron-icon> <strong>Hoster Rating</strong></span>
                              <span class="time"><iron-icon icon="device:access-time" title="Last Announcement"></iron-icon> <strong>Last announced</strong></span>
                            </li>
                          </ul>
                        </li>
                      <template is="dom-repeat" items="[[_split(release.supplement.class)]]" as="class">
                        <li>
                          <div style="display: inline-block; vertical-align: top; width: 17em; overflow: hidden">
                            [[class]]
                            <iron-icon hidden$="[[!_hasLocalRunningInstance(release.instances, class)]]" icon="hardware:security" title="Running locally"></iron-icon>
                            <iron-icon hidden$="[[!_hasOnlyRemoteRunningInstance(release.instances, class)]]" icon="icons:cloud" title="Running on network nodes"></iron-icon>
                          </div>
                          <ul style="display: inline-block; list-style: none; padding-left: 0">
                            <span hidden$="[[_hasRunningInstance(release.instances, class)]]">not running</span>
                            <template is="dom-repeat" items="[[_filterInstances(release.instances, class)]]" as="instance">
                              <li style="margin-left: 0">
                                <span class="nodeId">[[instance.nodeId]]</span>
                                <template is="dom-if" if="[[instance.nodeInfo.admin-name]]">
                                  <span class="nodeAdmin">
                                    [[instance.nodeInfo.admin-name]]
                                  </span>
                                  <span class="nodeAdminRating">
                                    <template is="dom-if" if="[[instance.hosterReputation]]">
                                      <custom-star-rating value="[[instance.hosterReputation]]" readonly single></custom-star-rating>
                                    </template>
                                    <template is="dom-if" if="[[!instance.hosterReputation]]">
                                      <custom-star-rating disable-rating readonly single></custom-star-rating>
                                    </template>
                                  </span>

                                </template>
                                <span class="time">[[_toHumanDate(instance.announcementEpochSeconds)]]</span>
                              </li>
                            </template>
                          </ul>
                        </li>
                      </template>
                    </ul>
                    <span style="margin-right:1em"><iron-icon icon="hardware:security" title="Running locally"></iron-icon> Microservice running locally</span>
                    <span><iron-icon icon="icons:cloud" title="Running on network nodes"></iron-icon> Microservice running remotely only</span>
                  </details>
                </div>
                <div class="card-actions">
                    <paper-button on-click="_handleStartButton"
                                  data-args$="[[service.name]]#[[_classesNotRunningLocally(release)]]@[[release.version]]" disabled$=[[_working]]>Start on this Node</paper-button>
                    <paper-button on-click="_handleStopButton"
                                  disabled$="[[!_countRunningLocally(release)]]"
                                  data-args$="[[service.name]]#[[release.supplement.class]]@[[release.version]]">Stop</paper-button>
                    <paper-button hidden$="[[!release.supplement.vcsUrl]]"
                                  on-click="_handleVcsButton"
                                  data-args$="[[release.supplement.vcsUrl]]">View source code</paper-button>
                    <paper-button hidden$="[[!release.supplement.frontendUrl]]"
                                  on-click="_handleFrontendButton"
                                  disabled$="[[!_fullyAvailableAnywhere(release)]]"
                                  data-args$="[[_frontendUrlIfServiceAvailable(release)]]">Open front-end</paper-button>
                    <paper-spinner style="padding: 0.7em;float: right;" active="[[_working]]"></paper-spinner>
                </div>
              </paper-card>
            </template>
          </template>
        </template>
      </div>
    `;
  }

  static get properties() {
    return {
      selectedRelease: { type: Object },
      apiEndpoint: { type: String, notify: true },
      agentId: { type: String, notify: true },
      error: { type: Object, notify: true },
      _nodeId: { type: Object }, // nested as .id FIXME
      _services: { type: Object },
      _communityTags: { type: Object },
      _submittingSearch: { type: Boolean },
      _submittingUpload: { type: Boolean },
      _hasNoEther: { type: Boolean, value: false },
      _working: { type: Boolean, value: false },
      _serviceString: { type: String, value: "@", notify: true },
    };
  }

  ready() {
    this.apiEndpoint = "http://localhost:8012/las2peer";
    super.ready();
    window.serviceThis = this;
    window.setTimeout(function () {
      window.serviceThis.refresh();
    }, 1);
    if (window.rootThis._isEthNode) {
      window.setInterval(function () {
        window.serviceThis.refresh();
      }, 5000);
    }
  }

  refresh() {
    if (window.rootThis._isEthNode) return;
    this.$.ajaxNodeId.generateRequest();
    this.$.ajaxServiceData.generateRequest();
    this.$.ajaxCommunityTags.generateRequest();
  }

  _sort(service, otherService) {
    try {
      // alphabetically by packagename
      return service.name < otherService.name ? -1 : 1;
    } catch (err) {
      return -1; // whatever
    }
  }

  _filter(service) {
    try {
      let query = this.$.filterField.value.trim();
      if (query.length < 1) return true;
      let serviceAsJson = JSON.stringify(service);
      return serviceAsJson.match(new RegExp(query, "i"));
    } catch (err) {
      return true;
    }
  }

  _triggerFilter(event) {
    this.$.serviceList.render();
  }

  _stringify(obj) {
    return JSON.stringify(obj);
  }

  _toArray(obj) {
    return Object.keys(obj).map((k) => ({ name: k, value: obj[k] }));
  }

  _toBool(obj) {
    return !!obj;
  }

  _split(stringWithCommas) {
    return (stringWithCommas || "").split(",");
  }

  _count(stringWithCommas) {
    return this._split(stringWithCommas).length;
  }

  _pluralS(stringWithCommas) {
    return this._count(stringWithCommas) > 1 ? "s" : "";
  }

  _toHumanDate(epochSeconds) {
    return new Date(epochSeconds * 1000).toLocaleString();
  }

  _getLatestVersionNumber(obj) {
    // NOTE: sorting issue fixed
    let latestVersion = Object.keys(obj)
      .map((a) =>
        a
          .split(".")
          .map((n) => +n + 1000000)
          .join(".")
      )
      .sort()
      .map((a) =>
        a
          .split(".")
          .map((n) => +n - 1000000)
          .join(".")
      )
      .reverse()[0];
    return latestVersion;
  }

  _getLatest(obj) {
    let latestVersionNumber = this._getLatestVersionNumber(obj);
    let latestRelease = obj[latestVersionNumber];
    // version number is key, let's add it so we can access it
    (latestRelease || {}).version = latestVersionNumber;
    return latestRelease;
  }

  _getLatestAsArray(obj) {
    return [this._getLatest(obj)];
  }

  _filterInstances(instances, serviceClass) {
    return instances.filter((i) => i.className === serviceClass);
  }

  _hasRunningInstance(instances, serviceClass) {
    return this._filterInstances(instances, serviceClass).length > 0;
  }

  _hasLocalRunningInstance(instances, serviceClass) {
    return (
      this._filterInstances(instances, serviceClass).filter(
        (i) => i.nodeId === (this._nodeId || {}).id
      ).length > 0
    );
  }

  _hasOnlyRemoteRunningInstance(instances, serviceClass) {
    return (
      this._hasRunningInstance(instances, serviceClass) &&
      !this._hasLocalRunningInstance(instances, serviceClass)
    );
  }

  _classesNotRunningAnywhere(release) {
    let classes = this._split((release.supplement || {}).class);
    let missing = classes.filter((c) => {
      let instancesOfClass = (release.instances || []).filter(
        (i) => i.className === c
      );
      return instancesOfClass < 1;
    });
    return missing;
  }

  _classesNotRunningLocally(release) {
    let classes = this._split((release.supplement || {}).class);
    let missing = classes.filter((c) => {
      let localInstancesOfClass = (release.instances || []).filter(
        (i) => i.className === c && i.nodeId === (this._nodeId || {}).id
      );
      return localInstancesOfClass < 1;
    });
    return missing;
  }

  // uh yeah, there's prettier ways to handle this
  _classesNotRunningLocallySeparatedByCommas(release) {
    return this._classesNotRunningLocally(release).join(",");
  }

  _countRunning(release) {
    let classes = this._split((release.supplement || {}).class);
    let missing = this._classesNotRunningAnywhere(release);
    return classes.length - missing.length;
  }

  _countRunningLocally(release) {
    let classes = this._split((release.supplement || {}).class);
    let missing = this._classesNotRunningLocally(release);
    return classes.length - missing.length;
  }

  _countMissingLocally(release) {
    return (
      this._count((release.supplement || {}).class) -
      this._countRunningLocally(release)
    );
  }

  _countRunningRemoteOnly(release) {
    return this._countRunning(release) - this._countRunningLocally(release);
  }

  // this counts several instances of a service class (in contrast, most other methods here ignore duplicates)
  _countInstancesRunningRemoteOnly(release) {
    return (
      (release.instances || "").length - this._countRunningLocally(release)
    );
  }

  _fullyAvailableAnywhere(release) {
    return this._classesNotRunningAnywhere(release).length === 0;
  }

  _fullyAvailableLocally(release) {
    return this._countMissingLocally(release) === 0;
  }

  _frontendUrlIfServiceAvailable(release) {
    if (this._fullyAvailableAnywhere(release)) {
      return (release.supplement || {}).frontendUrl;
    } else {
      return false;
    }
  }

  _clusterTypeAvailable(release) {
    console.log(release);
    if (!release) {
      return false;
    } else if (!release.supplement) {
      return false;
    } else if (release.supplement.type == "cae-application") {
      return true;
    } else {
      return false;
    }
  }

  _getNumberOfReleases(service) {
    return Object.keys(service.releases).length;
  }

  _getDeploymentsAsArray(releases, version) {
    return releases[version].instances;
  }
  _selectedRelease(){
    if(this.selectedRelease){
      console.log("true")
      return true;
    }
    else{
      console.log("false")

      return false;
    }
  }
  test(o){
    var args = o.target.getAttribute('data-args');
  }

  _keyPressedUploadService(event) {
    if (event.which == 13 || event.keyCode == 13) {
      event.preventDefault();
      this.uploadService();
      return false;
    }
    return true;
  }

  _handleStartButton(event) {
    let arg = event.target.getAttribute("data-args");
    let packageName = arg.split("#")[0];
    let version = arg.split("@")[1];
    let classes = arg.split("#")[1].split("@")[0].split(",");

    for (let c of classes) {
      this.startService(packageName + "." + c, version);
    }
  }

  _handleStartButtonNoChain(event) {
    let serviceString = this.shadowRoot
      .querySelector("#serviceString")
      .split("@");
    let serviceName = serviceString[0];
    let serviceVersion = serviceString[1];
    this.startService(serviceName, serviceVersion);
  }

  startService(fullClassName, version) {
    let req = this.$.ajaxStartService;
    req.params = { serviceName: fullClassName, version: version };
    console.log(
      "Requesting start of '" + fullClassName + "'@'" + version + "' ..."
    );
    req.generateRequest();
  }

  _handleStopButton(event) {
    let arg = event.target.getAttribute("data-args");
    let packageName = arg.split("#")[0];
    let version = arg.split("@")[1];
    let classes = arg.split("#")[1].split("@")[0].split(",");

    for (let c of classes) {
      this.stopService(packageName + "." + c, version);
    }
  }

  stopService(fullClassName, version) {
    let req = this.$.ajaxStopService;
    req.params = { serviceName: fullClassName, version: version };
    console.log(
      "Requesting stop of '" + fullClassName + "'@'" + version + "' ..."
    );
    req.generateRequest();
  }

  _handleVcsButton(event) {
    if (event.target.getAttribute("data-args")) {
      window.open(event.target.getAttribute("data-args"));
    }
  }

  _handleFrontendButton(event) {
    if (event.target.getAttribute("data-args")) {
      window.open(event.target.getAttribute("data-args"));
    }
  }

  _handleServiceStart(event) {
    window.rootThis.checkStatus();
  }

  _handleError(object, title, message) {
    window.rootThis._handleError(object, title, message);
  }
}

window.customElements.define("services-view", ServicesView);
