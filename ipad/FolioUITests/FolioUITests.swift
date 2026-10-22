import XCTest

final class FolioUITests:XCTestCase {
    func testCreateEditSettingsAndReopen() throws {
        let app=XCUIApplication(); app.launch()
        if app.buttons["skipWelcome"].waitForExistence(timeout:5) { app.buttons["skipWelcome"].tap() }
        XCTAssertTrue(app.buttons["createNotebook"].waitForExistence(timeout:10)); app.buttons["createNotebook"].tap()
        XCTAssertTrue(app.buttons["tool-pen"].waitForExistence(timeout:10))
        app.buttons["tool-marker"].tap(); app.buttons["tool-marker"].tap()
        XCTAssertTrue(app.sliders.firstMatch.waitForExistence(timeout:3))
        let screenshot=XCTAttachment(screenshot:app.screenshot()); screenshot.name="Folio iPad · tool settings"; screenshot.lifetime = .keepAlways; add(screenshot)
        app.buttons["Библиотека"].tap()
        XCTAssertTrue(app.buttons["createNotebook"].waitForExistence(timeout:10))
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["createNotebook"].waitForExistence(timeout:10))
        XCTAssertFalse(app.staticTexts["Здесь начнётся ваша история"].exists)
    }
}
