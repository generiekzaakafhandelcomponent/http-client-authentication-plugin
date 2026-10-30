/*
 * Copyright 2015-2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import {PluginConfigurationComponent} from "@valtimo/plugin";
import {Component, EventEmitter, Input, OnDestroy, OnInit, Output} from "@angular/core";
import {BehaviorSubject, combineLatest, Observable, Subscription, take} from "rxjs";
import {
    HttpClientAuthenticationPluginConfig
} from "../models/http-client-authentication-plugin-config";

type TlsSource = 'PEM' | 'KEYSTORE';

const PEM_FIELDS = ['clientCertificate', 'clientPrivateKey', 'serverCertificate'] as const;
const KEYSTORE_FIELDS = ['keystorePath', 'keystoreSecret', 'truststorePath', 'truststoreSecret'] as const;

// Checked here because Valtimo shows a refusal by the backend only as a generic error
const HEADER_NAME = /^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/;
const HTTP_URL = /^https?:\/\/[^\s/?#:]+[^\s]*$/i;
// What can be sent as a header value: tab, visible ASCII, space and Latin-1 without its control characters
const HEADER_VALUE = /^[\t\x20-\x7E\u00A0-\u00FF]*$/;
const MILLISECONDS = /^[1-9][0-9]{0,8}$/;

// Not secret, so trimmed on save; a secret is kept as typed
// Milliseconds, saved as a number: the backend properties are Int
const TIMEOUT_FIELDS = ['connectionTimeout', 'responseTimeout'];

const TRIMMED_FIELDS = [
    'configurationTitle', 'authHeaderName', 'username', 'clientId', 'tokenEndpoint', 'audience', 'scope',
    'clientCertificate', 'serverCertificate', 'keystorePath', 'truststorePath',
];

function filled(value: string | null | undefined): boolean {
    return !!value?.trim();
}

// Empty, or a value the backend can send as a header
function headerValue(value: string | null | undefined): boolean {
    return HEADER_VALUE.test(value ?? '');
}

// Empty, or a positive number of milliseconds
function timeout(value: string | number | null | undefined): boolean {
    const text = `${value ?? ''}`.trim();
    return !text || MILLISECONDS.test(text);
}

// PEM needs its line breaks: a header line, then the base64 lines, then the footer line
function isCertificate(text: string): boolean {
    return /-----BEGIN CERTIFICATE-----\r?\n[\s\S]+\r?\n\s*-----END CERTIFICATE-----/.test(text)
        && !text.includes('PRIVATE KEY-----');
}

function isUnencryptedPrivateKey(text: string): boolean {
    return (text.match(/-----BEGIN [A-Z ]*PRIVATE KEY-----\r?\n/g) ?? []).length === 1 && !text.includes('ENCRYPTED');
}

@Component({
    standalone: false,
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: 'http-client-authentication-plugin-configuration',
    templateUrl: './http-client-authentication-plugin-configuration.component.html',
    styles: [
        '.tls-toggle, .authentication-type-description { margin-block-end: var(--v-input-margin); }',
        // v-radio lays its options out in a row, which doesn't fit this many
        ':host ::ng-deep .authentication-type .cds--radio-button-group { flex-direction: column; align-items: flex-start; }',
        ':host ::ng-deep .authentication-type .cds--radio-button-wrapper { margin-inline-end: 0; }',
        ':host ::ng-deep .authentication-type .cds--radio-button-wrapper:not(:last-of-type) { margin-block-end: 0.5rem; }',
        '.authentication-type-description { max-width: 40rem; }',
    ],
})
export class HttpClientAuthenticationPluginConfigurationComponent
    // The component explicitly implements the PluginConfigurationComponent interface
    implements PluginConfigurationComponent, OnInit, OnDestroy {
    @Input() save$: Observable<void>;
    @Input() disabled$: Observable<boolean>;
    @Input() pluginId: string
    // If the plugin had already been saved, a prefilled configuration of the type HttpClientAuthenticationPluginConfig is expected
    @Input() prefillConfiguration$: Observable<HttpClientAuthenticationPluginConfig>;

    @Output() valid: EventEmitter<boolean> = new EventEmitter<boolean>();
    @Output() configuration: EventEmitter<HttpClientAuthenticationPluginConfig> =
        new EventEmitter<HttpClientAuthenticationPluginConfig>();

    readonly bearerRadio = {value: 'BEARER', title: 'Bearer'};
    readonly headerRadio = {value: 'HEADER', title: 'Header'};
    readonly tokenRadio = {value: 'TOKEN', title: 'Token'};
    readonly zgwJwtRadio = {value: 'ZGW_JWT', title: 'Client ID / secret (ZGW JWT)'};
    readonly tokenExchangeRadio = {value: 'TOKEN_EXCHANGE', title: 'Token exchange'};
    readonly basicRadio = {value: 'BASIC', title: 'Basic (username / password)'};
    readonly bearerAndApiKeyRadio = {value: 'BEARER_AND_API_KEY', title: 'Bearer and X-Api-Key'};
    readonly samlTokenRadio = {value: 'SAML_TOKEN', title: 'SAML token service'};
    readonly noneRadio = {value: 'NONE', title: 'None'};

    // A fixed secret first, then the types that create or fetch a token, then none
    readonly authenticationTypeOptions = [
        this.bearerRadio,
        this.tokenRadio,
        this.headerRadio,
        this.bearerAndApiKeyRadio,
        this.basicRadio,
        this.zgwJwtRadio,
        this.tokenExchangeRadio,
        this.samlTokenRadio,
        this.noneRadio,
    ];

    readonly pemRadio = {value: 'PEM', title: 'PEM'};
    readonly keystoreRadio = {value: 'KEYSTORE', title: 'Keystore (JKS / PKCS12)'};
    readonly tlsSourceOptions = [this.pemRadio, this.keystoreRadio];

    private saveSubscription!: Subscription;
    private prefillSubscription!: Subscription;

    private readonly formValue$ = new BehaviorSubject<HttpClientAuthenticationPluginConfig | null>(null);
    private readonly valid$ = new BehaviorSubject<boolean>(false);

    // Secrets are never sent back, so an unchanged field that owns a secret keeps its stored secret:
    // the client certificate its private key, the keystore paths their secrets, the authentication type its secret
    private prefilledClientCertificate = '';
    private prefilledKeystorePath = '';
    private prefilledTruststorePath = '';
    private prefilledAuthenticationType = '';

    selectedAuthType$ = new BehaviorSubject(this.bearerRadio)

    // Rebuilds the form for each prefilled configuration
    readonly formVersion$ = new BehaviorSubject<number>(0);

    // Frontend-only switch: the certificate fields are shown, validated and saved only while it is on
    readonly tlsEnabled$ = new BehaviorSubject<boolean>(false);

    // Secrets that become required by what is filled in, for their required marker
    privateKeyRequired = false;
    keystoreSecretRequired = false;
    truststoreSecretRequired = false;

    // Frontend-only choice: only the fields of the chosen source are shown, validated and saved
    readonly tlsSource$ = new BehaviorSubject<TlsSource>('PEM');

    ngOnInit(): void {
        this.prefillSubscription = this.prefillConfiguration$.subscribe(config => {
            this.formValue$.next(null);
            this.valid$.next(false);
            this.formVersion$.next(this.formVersion$.getValue() + 1);
            this.prefilledClientCertificate = config?.clientCertificate?.trim() ?? '';
            this.prefilledKeystorePath = config?.keystorePath?.trim() ?? '';
            this.prefilledTruststorePath = config?.truststorePath?.trim() ?? '';
            this.prefilledAuthenticationType = config?.authenticationType ?? '';
            const keystore = !!(this.prefilledKeystorePath || this.prefilledTruststorePath);
            this.tlsEnabled$.next(keystore || !!(this.prefilledClientCertificate || config?.serverCertificate?.trim()));
            this.tlsSource$.next(keystore ? 'KEYSTORE' : 'PEM');
            this.selectAuthType(config?.authenticationType);
        });
        this.openSaveSubscription();
    }

    ngOnDestroy() {
        this.saveSubscription?.unsubscribe();
        this.prefillSubscription?.unsubscribe();
    }

    formValueChange(formValue: any): void {
        this.formValue$.next(formValue);
        this.handleValid(formValue);
    }

    radioValueChange(radioValue: string): void {
        this.selectAuthType(radioValue);
    }

    tlsEnabledChange(tlsEnabled: boolean): void {
        this.tlsEnabled$.next(tlsEnabled);
        this.revalidate();
    }

    tlsSourceChange(tlsSource: string): void {
        if (tlsSource === 'PEM' || tlsSource === 'KEYSTORE') {
            this.tlsSource$.next(tlsSource);
            this.revalidate();
        }
    }

    private revalidate(): void {
        const formValue = this.formValue$.getValue();
        if (formValue) {
            this.handleValid(formValue);
        }
    }

    // The secret of a new configuration, or of another authentication type than the stored one, must be entered
    secretRequired(authenticationType: string | undefined): boolean {
        return authenticationType !== this.prefilledAuthenticationType;
    }

    private selectAuthType(authenticationType: string | undefined): void {
        const option = this.authenticationTypeOptions.find(option => option.value === authenticationType);
        if (option) {
            this.selectedAuthType$.next(option);
        }
    }

    private handleValid(formValue: HttpClientAuthenticationPluginConfig): void {
        const clientCertificate = formValue.clientCertificate?.trim() ?? '';
        this.privateKeyRequired = !!clientCertificate && clientCertificate !== this.prefilledClientCertificate;
        this.keystoreSecretRequired = this.storeSecretRequired(formValue.keystorePath, this.prefilledKeystorePath);
        this.truststoreSecretRequired = this.storeSecretRequired(formValue.truststorePath, this.prefilledTruststorePath);

        const valid = filled(formValue.configurationTitle)
            && this.authenticationValid(formValue)
            && (!this.tlsEnabled$.getValue() || this.certificatesValid(formValue));

        this.valid$.next(valid);
        this.valid.emit(valid);
    }

    private authenticationValid(formValue: HttpClientAuthenticationPluginConfig): boolean {
        const typeUnchanged = !this.secretRequired(formValue.authenticationType);
        const secretValid = filled(formValue.authSecret) || typeUnchanged;
        // A secret sent as header value, trimmed
        const headerSecretValid = secretValid && headerValue(formValue.authSecret?.trim());
        switch (formValue.authenticationType) {
            case this.bearerRadio.value:
            case this.tokenRadio.value:
            case this.bearerAndApiKeyRadio.value:
                return headerSecretValid;
            case this.headerRadio.value:
                return HEADER_NAME.test(formValue.authHeaderName?.trim() ?? '') && headerSecretValid;
            // Basic credentials are encoded, so any character goes
            case this.basicRadio.value: {
                const username = formValue.username?.trim() ?? '';
                return !!username && !username.includes(':') && secretValid;
            }
            case this.samlTokenRadio.value: {
                const headerName = formValue.authHeaderName?.trim() ?? '';
                const tokenEndpoint = formValue.tokenEndpoint?.trim() ?? '';
                return HTTP_URL.test(tokenEndpoint) && /^https:/i.test(tokenEndpoint)
                    && (!headerName || HEADER_NAME.test(headerName))
                    && timeout(formValue.connectionTimeout) && timeout(formValue.responseTimeout);
            }
            case this.zgwJwtRadio.value:
                return filled(formValue.clientId) && secretValid
                    && (!filled(formValue.authSecret) || formValue.authSecret.length >= 32);
            case this.tokenExchangeRadio.value:
                return HTTP_URL.test(formValue.tokenEndpoint?.trim() ?? '') && filled(formValue.clientId)
                    && (filled(formValue.clientSecret) || typeUnchanged);
            case this.noneRadio.value:
                return true;
            default:
                return false;
        }
    }

    private certificatesValid(formValue: HttpClientAuthenticationPluginConfig): boolean {
        if (this.tlsSource$.getValue() === 'KEYSTORE') {
            return this.storeValid(formValue.keystorePath, formValue.keystoreSecret, this.prefilledKeystorePath)
                && this.storeValid(formValue.truststorePath, formValue.truststoreSecret, this.prefilledTruststorePath);
        }
        const clientCertificate = formValue.clientCertificate?.trim() ?? '';
        const clientPrivateKey = formValue.clientPrivateKey?.trim() ?? '';
        const serverCertificate = formValue.serverCertificate?.trim() ?? '';
        if (serverCertificate && !isCertificate(serverCertificate)) {
            return false;
        }
        if (!clientCertificate) {
            return !clientPrivateKey;
        }
        if (!isCertificate(clientCertificate) || (clientPrivateKey && !isUnencryptedPrivateKey(clientPrivateKey))) {
            return false;
        }
        return !!clientPrivateKey || clientCertificate === this.prefilledClientCertificate;
    }

    private storeValid(path: string | undefined, secret: string | undefined, prefilledPath: string): boolean {
        return !this.storeSecretRequired(path, prefilledPath) || filled(secret);
    }

    private storeSecretRequired(path: string | undefined, prefilledPath: string): boolean {
        const trimmedPath = path?.trim() ?? '';
        return !!trimmedPath && trimmedPath !== prefilledPath;
    }

    // Leaving a field out of the saved configuration clears it; a stored secret stays, but is ignored without its field
    private withoutInactiveFields(formValue: HttpClientAuthenticationPluginConfig): HttpClientAuthenticationPluginConfig {
        const {tlsSource, ...configuration} = formValue as HttpClientAuthenticationPluginConfig & {tlsSource?: string};
        const inactiveFields: readonly string[] = !this.tlsEnabled$.getValue()
            ? [...PEM_FIELDS, ...KEYSTORE_FIELDS]
            : this.tlsSource$.getValue() === 'KEYSTORE' ? PEM_FIELDS : KEYSTORE_FIELDS;
        return Object.fromEntries(
            Object.entries(configuration)
                .filter(([field]) => !inactiveFields.includes(field))
                .map(([field, value]) => [field, typeof value === 'string' ? this.savedValue(field, value) : value])
        ) as HttpClientAuthenticationPluginConfig;
    }

    private savedValue(field: string, value: string): string | number | null {
        if (TIMEOUT_FIELDS.includes(field)) {
            return value.trim() ? Number(value.trim()) : null;
        }
        if (!value.trim()) {
            return '';
        }
        return TRIMMED_FIELDS.includes(field) ? value.trim() : value;
    }

    private openSaveSubscription(): void {
        /*
        If the save observable is triggered, check if the configuration is valid, and if so,
        output the configuration using the configuration EventEmitter.
         */
        this.saveSubscription = this.save$?.subscribe(save => {
            combineLatest([this.formValue$, this.valid$])
                .pipe(take(1))
                .subscribe(([formValue, valid]) => {
                    if (valid) {
                        this.configuration.emit(this.withoutInactiveFields(formValue));
                    }
                });
        });
    }
}
