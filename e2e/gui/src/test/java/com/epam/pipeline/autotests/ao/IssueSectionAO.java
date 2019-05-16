package com.epam.pipeline.autotests.ao;

import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.Keys;

import java.util.Map;

import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selectors.byAttribute;
import static com.codeborne.selenide.Selectors.byClassName;
import static com.codeborne.selenide.Selectors.byId;
import static com.codeborne.selenide.Selenide.$;
import static com.epam.pipeline.autotests.ao.Primitive.CANCEL;
import static com.epam.pipeline.autotests.ao.Primitive.CREATE;
import static com.epam.pipeline.autotests.ao.Primitive.DESCRIPTION;
import static com.epam.pipeline.autotests.ao.Primitive.NEW_ISSUE;
import static com.epam.pipeline.autotests.ao.Primitive.PREVIEW;
import static com.epam.pipeline.autotests.ao.Primitive.PREVIEW_TAB;
import static com.epam.pipeline.autotests.ao.Primitive.TITLE;
import static com.epam.pipeline.autotests.ao.Primitive.WRITE_TAB;
import static com.epam.pipeline.autotests.utils.PipelineSelectors.button;

public class IssueSectionAO extends PopupAO<IssueSectionAO, AccessObject> {
    private final Map<Primitive, SelenideElement> elements = initialiseElements(
            entry(NEW_ISSUE, context().find(button("New issue"))),
            entry(TITLE, $(byId("name"))),
            entry(WRITE_TAB, $(".ant-tabs").findAll(".ant-tabs-tab").find(text("Write"))),
            entry(PREVIEW_TAB, $(".ant-tabs").findAll(".ant-tabs-tab").find(text("Preview"))),
            entry(DESCRIPTION, $(".ant-mention-editor").find(byClassName("DraftEditor-editorContainer"))
                    .find(byAttribute("role", "textbox"))),
            entry(PREVIEW, $(byId("description-text-container"))),
            entry(CREATE, $(byId("create-issue-button"))),
            entry(CANCEL, $(byId("cancel-create-issue-button")))
    );

    public IssueSectionAO clickNewIssue() {
        click(NEW_ISSUE);
        return this;
    }

    public IssueSectionAO addNewIssue(final String title, final String description) {
        get(TITLE).shouldBe(enabled).sendKeys(Keys.chord(Keys.CONTROL), title);
        click(WRITE_TAB);
        click(DESCRIPTION);
        setValue(DESCRIPTION, description);
        click(CREATE);
        return this;
    }

    public IssueSectionAO(final AccessObject parentAO) {
        super(parentAO);
    }

    @Override
    public Map<Primitive, SelenideElement> elements() {
        return elements;
    }
}
