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

@Component({
    standalone: false,
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: 'http-client-authentication-plugin-configuration',
    templateUrl: './http-client-authentication-plugin-configuration.component.html',
    styles: ['.tls-toggle { margin-block-end: var(--v-input-margin); }'],
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
    readonly noneRadio = {value: 'NONE', title: 'None'};

    readonly authenticationTypeOptions = [
        this.bearerRadio,
        this.headerRadio,
        this.tokenRadio,
        this.zgwJwtRadio,
        this.tokenExchangeRadio,
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

    private selectAuthType(authenticationType: string | undefined): void {
        const option = this.authenticationTypeOptions.find(option => option.value === authenticationType);
        if (option) {
            this.selectedAuthType$.next(option);
        }
    }

    private handleValid(formValue: HttpClientAuthenticationPluginConfig): void {
        const valid = !!formValue.configurationTitle
            && this.authenticationValid(formValue)
            && (!this.tlsEnabled$.getValue() || this.certificatesValid(formValue));

        this.valid$.next(valid);
        this.valid.emit(valid);
    }

    private authenticationValid(formValue: HttpClientAuthenticationPluginConfig): boolean {
        const typeUnchanged = formValue.authenticationType === this.prefilledAuthenticationType;
        const secretValid = !!formValue.authSecret || typeUnchanged;
        switch (formValue.authenticationType) {
            case this.bearerRadio.value:
            case this.tokenRadio.value:
                return secretValid;
            case this.headerRadio.value:
                return !!formValue.authHeaderName && secretValid;
            case this.zgwJwtRadio.value:
                return !!formValue.clientId && secretValid && (!formValue.authSecret || formValue.authSecret.length >= 32);
            case this.tokenExchangeRadio.value:
                return !!formValue.tokenEndpoint && !!formValue.clientId && !!formValue.audience
                    && (!!formValue.clientSecret || typeUnchanged);
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
        if (!clientCertificate) {
            return !clientPrivateKey;
        }
        return !!clientPrivateKey || clientCertificate === this.prefilledClientCertificate;
    }

    private storeValid(path: string | undefined, secret: string | undefined, prefilledPath: string): boolean {
        const trimmedPath = path?.trim() ?? '';
        return !trimmedPath || !!secret || trimmedPath === prefilledPath;
    }

    // Leaving a field out of the saved configuration clears it; a stored secret stays, but is ignored without its field
    private withoutInactiveFields(formValue: HttpClientAuthenticationPluginConfig): HttpClientAuthenticationPluginConfig {
        const {tlsSource, ...configuration} = formValue as HttpClientAuthenticationPluginConfig & {tlsSource?: string};
        const inactiveFields: readonly string[] = !this.tlsEnabled$.getValue()
            ? [...PEM_FIELDS, ...KEYSTORE_FIELDS]
            : this.tlsSource$.getValue() === 'KEYSTORE' ? PEM_FIELDS : KEYSTORE_FIELDS;
        return Object.fromEntries(
            Object.entries(configuration).filter(([field]) => !inactiveFields.includes(field))
        ) as HttpClientAuthenticationPluginConfig;
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
