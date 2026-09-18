/*
 * Copyright (C) 2022-2024 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "libshims_fingerprint.oplus"

#include <cutils/log.h>
#include <sys/system_properties.h>

#include <dlfcn.h>
#include <string.h>

static int read_prop_callback(const char* name, char* value) {
    const prop_info* pi = __system_property_find(name);
    if (pi != nullptr) {
        struct CallbackData {
            char* val;
            int len;
        } data = { value, 0 };
        __system_property_read_callback(pi, [](void* cookie, const char*, const char* val, uint32_t) {
            auto* d = static_cast<CallbackData*>(cookie);
            d->len = strlen(strcpy(d->val, val));
        }, &data);
        return data.len;
    }
    return 0;
}

extern "C" int __system_property_get(const char* __name, char* __value) {
    if (!__name || !__value) return 0;

    if (strcmp(__name, "ro.boot.vbmeta.device_state") == 0) {
        ALOGV("Returning unlocked for ro.boot.vbmeta.device_state");
        return strlen(strcpy(__value, "unlocked"));
    }

    if (strcmp(__name, "ro.boot.verifiedbootstate") == 0) {
        ALOGV("Returning orange for ro.boot.verifiedbootstate");
        return strlen(strcpy(__value, "orange"));
    }

    if (strlen(__name) >= 32) {
        return read_prop_callback(__name, __value);
    }

    static auto __system_property_get_orig = reinterpret_cast<typeof(__system_property_get)*>(
            dlsym(RTLD_NEXT, "__system_property_get"));
    if (__system_property_get_orig) {
        return __system_property_get_orig(__name, __value);
    }
    return read_prop_callback(__name, __value);
}

extern "C" int property_get(const char* key, char* value, const char* default_value) {
    if (!key || !value) return 0;

    if (strcmp(key, "ro.boot.vbmeta.device_state") == 0) {
        ALOGV("Returning unlocked for ro.boot.vbmeta.device_state");
        return strlen(strcpy(value, "unlocked"));
    }

    if (strcmp(key, "ro.boot.verifiedbootstate") == 0) {
        ALOGV("Returning orange for ro.boot.verifiedbootstate");
        return strlen(strcpy(value, "orange"));
    }

    if (strlen(key) >= 32) {
        int len = read_prop_callback(key, value);
        if (len > 0) return len;
        if (default_value) {
            return strlen(strcpy(value, default_value));
        }
        value[0] = '\0';
        return 0;
    }

    static auto property_get_orig =
            reinterpret_cast<typeof(property_get)*>(dlsym(RTLD_NEXT, "property_get"));
    if (property_get_orig) {
        return property_get_orig(key, value, default_value);
    }
    int len = read_prop_callback(key, value);
    if (len > 0) return len;
    if (default_value) {
        return strlen(strcpy(value, default_value));
    }
    value[0] = '\0';
    return 0;
}
