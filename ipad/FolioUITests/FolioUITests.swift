import XCTest

final class FolioUITests:XCTestCase {
    func testCreateEditSettingsAndReopen() throws {
        let app=XCUIApplication(); app.launch()
        if app.buttons["skipWelcome"].waitForExistence(timeout:5) { shot(app,"Folio iPad - welcome"); app.buttons["skipWelcome"].tap() }
        shot(app,"Folio iPad - library")
        XCTAssertTrue(app.buttons["createNotebook"].waitForExistence(timeout:10)); app.buttons["createNotebook"].tap()
        XCTAssertTrue(app.buttons["tool-pen"].waitForExistence(timeout:10))
        app.buttons["tool-marker"].tap(); app.buttons["tool-marker"].tap()
        XCTAssertTrue(app.sliders.firstMatch.waitForExistence(timeout:3))
        shot(app,"Folio iPad - tool settings")
        app.buttons["Библиотека"].tap()
        XCTAssertTrue(app.buttons["createNotebook"].waitForExistence(timeout:10))
        app.terminate(); app.launch()
        XCTAssertTrue(app.buttons["createNotebook"].waitForExistence(timeout:10))
        XCTAssertFalse(app.staticTexts["Здесь начнётся ваша история"].exists)
    }
    func shot(_ app:XCUIApplication,_ name:String) {
        let screenshot=XCTAttachment(screenshot:app.screenshot()); screenshot.name=name; screenshot.lifetime = .keepAlways; add(screenshot)
    }
}
