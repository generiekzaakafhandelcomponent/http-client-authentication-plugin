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

@Component({
    standalone: false,
    // eslint-disable-next-line @angular-eslint/component-selector
    selector: 'http-client-authentication-plugin-configuration',
    templateUrl: './http-client-authentication-plugin-configuration.component.html',
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

    readonly authenticationTypeOptions = [
        {value: 'BEARER', title: 'Bearer'},
        {value: 'HEADER', title: 'Header'},
        {value: 'TOKEN_EXCHANGE', title: 'Token exchange'},
        {value: 'NONE', title: 'None'},
    ];

    private saveSubscription!: Subscription;
    private prefillSubscription!: Subscription;

    private readonly formValue$ = new BehaviorSubject<HttpClientAuthenticationPluginConfig | null>(null);
    private readonly valid$ = new BehaviorSubject<boolean>(false);

    readonly selectedAuthType$ = new BehaviorSubject<string>('BEARER');

    ngOnInit(): void {
        this.prefillSubscription = this.prefillConfiguration$?.subscribe(config => {
            this.radioValueChange(config?.authenticationType);
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
        if (this.authenticationTypeOptions.some(option => option.value === radioValue)) {
            this.selectedAuthType$.next(radioValue);
        }
    }

    private handleValid(formValue: HttpClientAuthenticationPluginConfig): void {
        // The fields each authentication type needs; mTLS (token exchange only) is optional, but each store needs its secret
        const requiredFields: Record<string, Array<keyof HttpClientAuthenticationPluginConfig>> = {
            BEARER: ['authSecret'],
            HEADER: ['authHeaderName', 'authSecret'],
            TOKEN_EXCHANGE: ['tokenEndpoint', 'clientId', 'clientSecret', 'audience'],
            NONE: [],
        };
        const fields = requiredFields[formValue.authenticationType];
        const valid = !!(
            formValue.configurationTitle &&
            fields &&
            fields.every(field => !!formValue[field]) &&
            (!formValue.keystorePath || formValue.keystoreSecret) &&
            (!formValue.truststorePath || formValue.truststoreSecret)
        );

        this.valid$.next(valid);
        this.valid.emit(valid);
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
                        this.configuration.emit(formValue);
                    }
                });
        });
    }
}
