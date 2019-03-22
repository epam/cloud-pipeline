/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.SelenideElement;

import java.util.Collections;
import java.util.Map;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;

public abstract class PopupAO<ELEMENT_AO extends PopupAO<ELEMENT_AO, PARENT_AO>, PARENT_AO>
        implements AccessObject<ELEMENT_AO>, ClosableAO {

    private final PARENT_AO parentAO;

    public PopupAO(PARENT_AO parentAO) {
        this.parentAO = parentAO;
    }

    public PARENT_AO cancel() {
        context().find(button("Cancel")).shouldBe(visible).click();
        return parent();
    }

    public PARENT_AO ok() {
        context().find(button("OK")).shouldBe(visible).click();
        return parent();
    }

    public PARENT_AO parent() {
        return parentAO;
    }

    @Override
    public void closeAll() {
        ok();
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return Collections.emptyMap();
    }
}
