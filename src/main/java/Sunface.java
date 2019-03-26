import javafx.animation.*;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.Rectangle2D;
import javafx.scene.*;
import javafx.scene.chart.LineChart;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.*;

public class Sunface extends Application {


    private int fpsSetting = (int) floor(1000 / Sunconfig.DEFAULT_FPS);

    private GregorianCalendar currentLocalTime;
    private GregorianCalendar offsetLocalTime;
    private double longitude = Sunconfig.DEFAULT_LONGITUDE;
    private double latitude = Sunconfig.DEFAULT_LATITUDE;
    private double customLongitude = Sunconfig.DEFAULT_LONGITUDE;
    private double customLatitude = Sunconfig.DEFAULT_LATITUDE;

    private enum PositionType {LONGITUDE, LATITUDE, BOTH, GOOGLE_MAPS};
    private enum OffsetType {YEAR, MONTH, DAY, HOUR, MINUTE, SECOND, WEEK};

    private double deltaX;
    private double deltaY;
    private double savedMouseX;
    private double savedMouseY;
    private int dX;
    private int dY;
    private double offsetX;
    private double offsetY;
    private double savedWindowPositionX;
    private double savedWindowPositionY;
    private double savedWindowSizeX;
    private double savedWindowSizeY;
    private double savedLongitude = longitude;
    private double savedLatitude = latitude;

    private boolean maximizedEh = false;
    private boolean snapToCenterEh = true;
    private boolean alwaysOnTopEh = false;

    private TextArea debugTextArea;
    private String debugErrorMessage;

    private Suntime suntimeLocal;
    private Suntime suntimeGlobal;

    private int timeZoneOffset;

    private Cetustime cetustime;
    private RefreshCetusDataTask refreshCetusDataTask;

    private Sunchart sunchart;

    private Stage debugWindow;
    private Stage sunchartWindow;

    private ArrayList<ArrayList<GregorianCalendar>> cetusNightList;

    private ArrayList<MouseButton> mouseButtonList = new ArrayList<>();


    public static void main(String[] args) {
        launch(args);
    }


    @Override
    public void start(Stage primaryStage) {

        // Init time
        currentLocalTime = new GregorianCalendar();
        offsetLocalTime = new GregorianCalendar();

        timeZoneOffset = currentLocalTime.getTimeZone().getRawOffset();

        // Create 'sun' objects
        suntimeLocal = new Suntime.PleaseBuildSuntime()
                .localTime(currentLocalTime)
                .observerLongitude(longitude)
                .observerLatitude(latitude)
                .thankYou();

        suntimeGlobal = new Suntime.PleaseBuildSuntime()
                .localTime(currentLocalTime)
                .observerLongitude(longitude)
                .observerLatitude(latitude)
                .thankYou();

        Sundial sundial = new Sundial.PleaseBuildSundial()
                .nightCompression(0)
                .thankYou();

        sundial.rotateGlobe(longitude, latitude);

        cetustime = new Cetustime();

        sunchart = new Sunchart(longitude, latitude, currentLocalTime.get(Calendar.YEAR));


        // Scene
        Group dialsGroup = sundial.getDialsGroup();

        Scene mainScene = new Scene(dialsGroup, dialsGroup.getLayoutBounds().getWidth(), dialsGroup.getLayoutBounds().getHeight(), true, SceneAntialiasing.DISABLED);
        mainScene.setFill(Color.TRANSPARENT);


        // Setup dialsGroup scale transform and bind to primaryStage size
        Scale dialsScale = new Scale();
        dialsGroup.getTransforms().add(dialsScale);

        dialsScale.xProperty().bind(Bindings.createDoubleBinding(() ->
                (primaryStage.widthProperty().get() / Sunconfig.DIAL_WIDTH), primaryStage.widthProperty()));

        dialsScale.yProperty().bind(Bindings.createDoubleBinding(() ->
                (primaryStage.heightProperty().get() / Sunconfig.DIAL_HEIGHT), primaryStage.heightProperty()));

        dialsScale.zProperty().bind(Bindings.createDoubleBinding(() -> {
            // For Z scale pick smaller value between width and height
            double stageWidth = primaryStage.widthProperty().get();
            double stageHeight = primaryStage.heightProperty().get();
            double stageSize = (stageWidth > stageHeight) ? stageHeight : stageWidth;
            double dialsSize = (Sunconfig.DIAL_WIDTH > Sunconfig.DIAL_HEIGHT) ? Sunconfig.DIAL_HEIGHT : Sunconfig.DIAL_WIDTH;
            return stageSize / dialsSize;
        }, primaryStage.widthProperty(), primaryStage.heightProperty()));



        // App icons
        Image appIconSun = new Image(Sunconfig.ICON_APP_URL, 512, 512, true, true);


        // Debug window
        debugTextArea = new TextArea();
        debugTextArea.setMinWidth(600);
        debugTextArea.setMinHeight(800);
        debugTextArea.setEditable(false);
        debugTextArea.setWrapText(true);
        debugTextArea.setText(Sunconfig.A_BEGINNING);

        Group debugGroup = new Group();
        debugGroup.getChildren().add(debugTextArea);

        Scene debugScene = new Scene(debugGroup, debugGroup.getLayoutBounds().getWidth(), debugGroup.getLayoutBounds().getHeight());

        debugWindow = new Stage();
        debugWindow.setTitle("Debug window");
        debugWindow.setScene(debugScene);
        debugWindow.setWidth(debugScene.getWidth());
        debugWindow.setHeight(debugScene.getHeight());
        debugWindow.setX(0);
        debugWindow.setY(0);
        debugWindow.setResizable(true);
        debugWindow.getIcons().add(appIconSun);


        // Chart window
        LineChart lineChart = sunchart.getChart();
        Scene chartScene = new Scene(lineChart, 800, 600);

        sunchartWindow = new Stage();
        sunchartWindow.setTitle("Sunchart");
        sunchartWindow.setScene(chartScene);
        sunchartWindow.setX(0);
        sunchartWindow.setY(0);
        sunchartWindow.setWidth(chartScene.getWidth());
        sunchartWindow.setHeight(chartScene.getHeight());
        sunchartWindow.getIcons().add(appIconSun);


        // Primary window
        primaryStage.setTitle("Sunface");
        primaryStage.setScene(mainScene);
        primaryStage.setMinWidth(Sunconfig.MIN_WIDTH);
        primaryStage.setMinHeight(Sunconfig.MIN_HEIGHT);
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.getIcons().add(appIconSun);


        // Playtime
        KeyFrame keyframeClockTick = new KeyFrame(
                Duration.millis(fpsSetting),
                event -> updateCurrentTime(sundial));

        Timeline timeline = new Timeline(keyframeClockTick);
        timeline.setCycleCount(Animation.INDEFINITE);


        // Events
        sundial.getControlThingyHelp().setOnMouseClicked(event -> sundial.toggleHelp());

        sundial.getControlThingyClose().setOnMouseClicked(event -> System.exit(0));

        sundial.getControlThingyMaximize().setOnMouseClicked(event -> { toggleMaximizeWindow(primaryStage, event); sundial.getControlThingyMaximize().toggle(); });

        sundial.getControlThingyMinimize().setOnMousePressed(event -> mouseButtonList.add(event.getButton()));
        sundial.getControlThingyMinimize().setOnMouseReleased(event -> { minimizeWindow(primaryStage, timeline, event); mouseButtonList.clear(); });

        sundial.getControlThingyNightmode().setOnMouseClicked(event -> sundial.toggleNightmode());

        sundial.getControlThingyAlwaysOnTop().setOnMouseClicked(event -> toggleAlwaysOnTop(primaryStage, sundial));

        sundial.getControlThingyGlobeGrid().setOnMouseClicked(event -> sundial.toggleGlobeGrid());

        sundial.getMatrixTimeZone().setOnMousePressed(event -> {
            mouseButtonList.add(event.getButton());
            changeTimeZone(sundial, event);
        });
        sundial.getMatrixTimeZone().setOnMouseReleased(event -> mouseButtonList.clear());
        sundial.getMatrixTimeZone().setOnMouseDragged(event -> changeTimeZone(sundial, event));
        sundial.getMatrixTimeZone().setOnScroll(event -> changeTimeZone(sundial, event));

        sundial.getControlNightCompression().setOnMousePressed(event -> recordNightCompressionPosition(sundial, event));
        sundial.getControlNightCompression().setOnMouseReleased(event -> mouseButtonList.clear());
        sundial.getControlNightCompression().setOnMouseDragged(event -> changeNightCompression(sundial, event));
        sundial.getControlNightCompression().setOnScroll(event -> changeNightCompression(sundial, event));

        sundial.getTinyGlobeGroup().setOnMousePressed(event -> mouseButtonList.add(event.getButton()));
        sundial.getTinyGlobeGroup().setOnMouseReleased(event -> {
            if (!mouseButtonList.isEmpty()) {
                tinyGlobeAction(sundial, event);
            }
            mouseButtonList.clear();
        });
        sundial.getTinyGlobeGroup().setOnDragOver(event -> {
            if (event.getGestureSource() != sundial.getTinyGlobeGroup() && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });
        sundial.getTinyGlobeGroup().setOnDragDropped(event -> rotateGlobe(sundial, PositionType.GOOGLE_MAPS, event));

        sundial.getMatrixLongitude().setOnMousePressed(event -> recordGlobePosition(sundial, PositionType.LONGITUDE, event));
        sundial.getMatrixLongitude().setOnMouseReleased(event -> {
            if (!mouseButtonList.isEmpty()) {
                MouseButton mouseButton = mouseButtonList.get(mouseButtonList.size() - 1);
                mouseButtonList.clear();
                if (mouseButton.equals(MouseButton.MIDDLE)) {
                    resetGlobePosition(sundial, PositionType.LONGITUDE); }
                else {
                    updateSunchart(sunchart);
                }
            }
            mouseButtonList.clear();
        });
        sundial.getMatrixLongitude().setOnMouseDragged(event -> rotateGlobe(sundial, PositionType.LONGITUDE, event));
        sundial.getMatrixLongitude().setOnScroll(event -> { rotateGlobe(sundial, PositionType.LONGITUDE, event); });

        sundial.getMatrixLatitude().setOnMousePressed(event -> recordGlobePosition(sundial, PositionType.LATITUDE, event));
        sundial.getMatrixLatitude().setOnMouseReleased(event -> {
            if (!mouseButtonList.isEmpty()) {
                MouseButton mouseButton = mouseButtonList.get(mouseButtonList.size() - 1);
                mouseButtonList.clear();
                if (mouseButton.equals(MouseButton.MIDDLE)) {
                    resetGlobePosition(sundial, PositionType.LATITUDE); }
                else {
                    updateSunchart(sunchart);
                }
            }
            mouseButtonList.clear();
        });
        sundial.getMatrixLatitude().setOnMouseDragged(event -> rotateGlobe(sundial, PositionType.LATITUDE, event));
        sundial.getMatrixLatitude().setOnScroll(event -> { rotateGlobe(sundial, PositionType.LATITUDE, event); });

        sundial.getControlThingyResize().setOnMousePressed(event -> recordWindowSize(primaryStage, dialsGroup, event));
        sundial.getControlThingyResize().setOnMouseReleased(event -> mouseButtonList.clear());
        sundial.getControlThingyResize().setOnMouseDragged(event -> resizeWindow(primaryStage, sundial, event));

        sundial.getDialMarginCircle().setOnMousePressed(event -> recordWindowPosition(primaryStage, event));
        sundial.getDialMarginCircle().setOnMouseReleased(event -> mouseButtonList.clear());
        sundial.getDialMarginCircle().setOnMouseDragged(event -> changeWindowPosition(primaryStage, event));

        sundial.getDialCircleFrame().setOnMousePressed(event -> {
            if (sundial.globeVisibleEh) {
                recordGlobePosition(sundial, PositionType.BOTH, event);
                if (!event.isMiddleButtonDown()) { sundial.setTimeDisplayOpacity(0.2); }
            } else {
                recordWindowPosition(primaryStage, event);
            }
        });
        sundial.getDialCircleFrame().setOnMouseReleased(event -> {
            if (sundial.globeVisibleEh) {
                sundial.setTimeDisplayOpacity(1);
                if (!mouseButtonList.isEmpty()) {
                    MouseButton mouseButton = mouseButtonList.get(mouseButtonList.size() - 1);
                    mouseButtonList.clear();
                    if (mouseButton.equals(MouseButton.MIDDLE)) {
                        resetGlobePosition(sundial, PositionType.BOTH); }
                    else {
                        updateSunchart(sunchart);
                    }
                }
            }
            mouseButtonList.clear();
        });
        sundial.getDialCircleFrame().setOnMouseDragged(event -> {
            if (sundial.globeVisibleEh) {
                rotateGlobe(sundial, event);
            } else {
                changeWindowPosition(primaryStage, event);
            }
        });

        sundial.getMatrixYear().setOnMousePressed(event -> recordCalendarPosition(sundial, event));
        sundial.getMatrixYear().setOnMouseReleased(event -> { mouseButtonList.clear(); updateSunchart(sunchart); });
        sundial.getMatrixYear().setOnMouseDragged(event -> offsetTime(sundial, OffsetType.YEAR, event));
        sundial.getMatrixYear().setOnScroll(event -> offsetTime(sundial, OffsetType.YEAR, event));

        sundial.getMatrixMonth().setOnMousePressed(event -> recordCalendarPosition(sundial, event));
        sundial.getMatrixMonth().setOnMouseReleased(event -> { mouseButtonList.clear();  updateSunchart(sunchart); });
        sundial.getMatrixMonth().setOnMouseDragged(event -> offsetTime(sundial, OffsetType.MONTH, event));
        sundial.getMatrixMonth().setOnScroll(event -> offsetTime(sundial, OffsetType.MONTH, event));

        sundial.getMatrixDay().setOnMousePressed(event -> recordCalendarPosition(sundial, event));
        sundial.getMatrixDay().setOnMouseReleased(event -> { mouseButtonList.clear();  updateSunchart(sunchart); });
        sundial.getMatrixDay().setOnMouseDragged(event -> offsetTime(sundial, OffsetType.DAY, event));
        sundial.getMatrixDay().setOnScroll(event -> offsetTime(sundial, OffsetType.DAY, event));

        sundial.getMatrixHour().setOnMousePressed(event -> recordCalendarPosition(sundial, event));
        sundial.getMatrixHour().setOnMouseReleased(event -> { mouseButtonList.clear();  updateSunchart(sunchart); });
        sundial.getMatrixHour().setOnMouseDragged(event -> offsetTime(sundial, OffsetType.HOUR, event));
        sundial.getMatrixHour().setOnScroll(event -> offsetTime(sundial, OffsetType.HOUR, event));

        sundial.getMatrixMinute().setOnMousePressed(event -> recordCalendarPosition(sundial, event));
        sundial.getMatrixMinute().setOnMouseReleased(event -> { mouseButtonList.clear();  updateSunchart(sunchart); });
        sundial.getMatrixMinute().setOnMouseDragged(event -> offsetTime(sundial, OffsetType.MINUTE, event));
        sundial.getMatrixMinute().setOnScroll(event -> offsetTime(sundial, OffsetType.MINUTE, event));

        sundial.getMatrixWeek().setOnMousePressed(event -> recordCalendarPosition(sundial, event));
        sundial.getMatrixWeek().setOnMouseReleased(event -> { mouseButtonList.clear();  updateSunchart(sunchart); });
        sundial.getMatrixWeek().setOnMouseDragged(event -> offsetTime(sundial, OffsetType.WEEK, event));
        sundial.getMatrixWeek().setOnScroll(event -> offsetTime(sundial, OffsetType.WEEK, event));

        sundial.getDialHighNoonGroup().setOnMouseClicked(event -> sundial.toggleAnimation());

        sundial.getMatrixDayLength().setOnMouseClicked(event -> toggleSunchartWindow(primaryStage));

        primaryStage.setOnHidden(event -> timeline.pause());
        primaryStage.setOnShown(event -> timeline.play());


        // Showtime
        initCurrentTime(sundial);
        primaryStage.show();
        timeline.play();

        recordWindowPosition(primaryStage, null);
        setAlwaysOnTop(primaryStage, sundial, alwaysOnTopEh);

    }

    // Methods
    private void resetTime(Sundial sundial) {
        offsetLocalTime.set(
                currentLocalTime.get(Calendar.YEAR),
                currentLocalTime.get(Calendar.MONTH),
                currentLocalTime.get(Calendar.DAY_OF_MONTH),
                currentLocalTime.get(Calendar.HOUR_OF_DAY),
                currentLocalTime.get(Calendar.MINUTE),
                currentLocalTime.get(Calendar.SECOND)
        );
        sundial.setDialFrameWarning(false);
        initCurrentTime(sundial);
    }

    private void offsetTime(Sundial sundial, OffsetType offsetType, ScrollEvent event) {

        if (suntimeLocal == null || sundial == null || event == null) { return; }

        if (!mouseButtonList.isEmpty()) { return; }

        int offsetFactor = 0;
        int offsetYear = 0;
        int offsetMonth = 0;
        int offsetDay = 0;
        int offsetHour = 0;
        int offsetMinute = 0;
        int offsetSecond = 0;
        int offsetWeek = 0;

        if (event.getDeltaY() < 0) { offsetFactor = -1; }
        else if (event.getDeltaY() > 0) { offsetFactor = 1; }

        switch (offsetType) {
            case YEAR   : offsetYear = offsetFactor; break;
            case MONTH  : offsetMonth = offsetFactor; break;
            case DAY    : offsetDay = offsetFactor; break;
            case HOUR   : offsetHour = offsetFactor; break;
            case MINUTE : offsetMinute = offsetFactor; break;
            case SECOND : offsetSecond = offsetFactor; break;
            case WEEK   : offsetWeek = offsetFactor; break;
            default: {}
        }

        offsetLocalTime.set(
                offsetLocalTime.get(Calendar.YEAR) + offsetYear,
                offsetLocalTime.get(Calendar.MONTH) + offsetMonth,
                offsetLocalTime.get(Calendar.DAY_OF_MONTH) + offsetDay,
                offsetLocalTime.get(Calendar.HOUR_OF_DAY) + offsetHour,
                offsetLocalTime.get(Calendar.MINUTE) + offsetMinute,
                offsetLocalTime.get(Calendar.SECOND) + offsetSecond
        );

        offsetLocalTime.setTimeInMillis(offsetLocalTime.getTimeInMillis() + offsetWeek * 7 * 24 * 60 * 60 * 1000);

        if (offsetLocalTime.equals(currentLocalTime)) { sundial.setDialFrameWarning(false); }
        else { sundial.setDialFrameWarning(true); }

        initCurrentTime(sundial);
    }

    private void offsetTime(Sundial sundial, OffsetType offsetType, MouseEvent event) {

        if (sundial == null || event == null) { return; }

        if (!mouseButtonList.isEmpty()) {
            MouseButton mouseButton = mouseButtonList.get(mouseButtonList.size() - 1);
            if (mouseButton.equals(MouseButton.MIDDLE)) {
                return;
            }
        }

        double mouseX = event.getScreenX();
        double mouseY = event.getScreenY();

        double deltaMouseX = mouseX - savedMouseX;
        double deltaMouseY = mouseY - savedMouseY;

        int offsetFactor = 0;
        int offsetYear = 0;
        int offsetMonth = 0;
        int offsetDay = 0;
        int offsetHour = 0;
        int offsetMinute = 0;
        int offsetSecond = 0;
        int offsetWeek = 0;

        double stepSize = Sunconfig.NORMAL_STEP_SIZE;
        if (event.isSecondaryButtonDown()) { stepSize = Sunconfig.FAST_STEP_SIZE; }

        if (deltaMouseY >= stepSize) {
            offsetFactor = -1;
            savedMouseY = mouseY;
        } else if (deltaMouseY <= -1 * stepSize) {
            offsetFactor = 1;
            savedMouseY = mouseY;
        } else {
            return;
        }

        switch (offsetType) {
            case YEAR   : offsetYear = offsetFactor; break;
            case MONTH  : offsetMonth = offsetFactor; break;
            case DAY    : offsetDay = offsetFactor; break;
            case HOUR   : offsetHour = offsetFactor; break;
            case MINUTE : offsetMinute = offsetFactor; break;
            case SECOND : offsetSecond = offsetFactor; break;
            case WEEK   : offsetWeek = offsetFactor; break;
            default: {}
        }

        offsetLocalTime.set(
                offsetLocalTime.get(Calendar.YEAR) + offsetYear,
                offsetLocalTime.get(Calendar.MONTH) + offsetMonth,
                offsetLocalTime.get(Calendar.DAY_OF_MONTH) + offsetDay,
                offsetLocalTime.get(Calendar.HOUR_OF_DAY) + offsetHour,
                offsetLocalTime.get(Calendar.MINUTE) + offsetMinute,
                offsetLocalTime.get(Calendar.SECOND) + offsetSecond
        );

        offsetLocalTime.setTimeInMillis(offsetLocalTime.getTimeInMillis() + offsetWeek * 7 * 24 * 60 * 60 * 1000);

        if (offsetLocalTime.equals(currentLocalTime)) { sundial.setDialFrameWarning(false); }
        else { sundial.setDialFrameWarning(true); }

        initCurrentTime(sundial);
    }

    private void changeTimeZone(Sundial sundial, MouseEvent event) {

        if (sundial == null || event == null) { return; }

        if (!mouseButtonList.isEmpty()) {
            if (mouseButtonList.get(mouseButtonList.size() - 1).equals(MouseButton.MIDDLE)) {
                TimeZone localTimeZone = (new GregorianCalendar()).getTimeZone();
                timeZoneOffset = localTimeZone.getRawOffset();
            }
        }

        currentLocalTime.getTimeZone().setRawOffset(timeZoneOffset);
        offsetLocalTime.getTimeZone().setRawOffset(timeZoneOffset);

        initCurrentTime(sundial);
    }

    private void changeTimeZone(Sundial sundial, ScrollEvent event) {

        if (sundial == null || event == null) { return; }

        if (!mouseButtonList.isEmpty()) { return; }

        if (event.getDeltaY() < 0) {
            timeZoneOffset -= (60 * 60 * 1000);
        }
        else if (event.getDeltaY() > 0) {
            timeZoneOffset += (60 * 60 * 1000);
        }
        else { return; }

        if (timeZoneOffset > (12 * 60 * 60 * 1000)) {
            timeZoneOffset = -11 * 60 * 60 * 1000;
        }

        if (timeZoneOffset <= (-12 * 60 * 60 * 1000)) {
            timeZoneOffset =  12 * 60 * 60 * 1000;
        }

        currentLocalTime.getTimeZone().setRawOffset(timeZoneOffset);
        offsetLocalTime.getTimeZone().setRawOffset(timeZoneOffset);

        initCurrentTime(sundial);
    }

    private void changeNightCompression(Sundial sundial, ScrollEvent event) {

        if (suntimeLocal == null || sundial == null || event == null) { return; }

        if (!mouseButtonList.isEmpty()) { return; }

        if (event.getDeltaY() < 0) { sundial.increaseNightCompression(); }
        else if (event.getDeltaY() > 0) { sundial.decreaseNightCompression(); }
        else { return; }

    }

    private void changeNightCompression(Sundial sundial, MouseEvent event) {

        if (suntimeLocal == null || sundial == null || event == null) { return; }

        MouseButton mouseButton = mouseButtonList.get(mouseButtonList.size() - 1);
        if (mouseButton == null || mouseButton.equals(MouseButton.MIDDLE)) { return; }

        double mouseX = event.getScreenX();
        double mouseY = event.getScreenY();

        double deltaMouseX = mouseX - savedMouseX;
        double deltaMouseY = mouseY - savedMouseY;

        double stepSize = Sunconfig.NORMAL_STEP_SIZE;
        if (event.isSecondaryButtonDown()) { stepSize = Sunconfig.FAST_STEP_SIZE; }

        if (deltaMouseY >= stepSize) {
            sundial.increaseNightCompression();
            savedMouseY = mouseY;
        } else if (deltaMouseY <= -1 * stepSize) {
            sundial.decreaseNightCompression();
            savedMouseY = mouseY;
        } else {
            return;
        }

    }

    private void initCurrentTime(Sundial sundial) {
        updateCurrentTime(sundial, true);
    }

    private void updateCurrentTime(Sundial sundial) {
        updateCurrentTime(sundial, false);
    }

    private void updateCurrentTime(Sundial sundial, boolean initialize) {

        GregorianCalendar newLocalTime = new GregorianCalendar();

        // Store current Julian Day Number before updating current time
        long oldJulianDayNumber = Suntime.getJulianDayNumber(offsetLocalTime);

        long newTimeInSeconds = newLocalTime.getTimeInMillis() / 1000;
        long currentTimeInSeconds = currentLocalTime.getTimeInMillis() / 1000;

        if (newTimeInSeconds == currentTimeInSeconds && !initialize) { return; }

        // Update current and offset local time
        long offsetSeconds = (offsetLocalTime.getTimeInMillis() - currentLocalTime.getTimeInMillis()) / 1000;
        currentLocalTime = newLocalTime;
        offsetLocalTime.setTimeInMillis(currentLocalTime.getTimeInMillis() + offsetSeconds * 1000);

        // Update suntime and sundial objects
        long timeZoneCorrection = offsetLocalTime.getTimeZone().getOffset(offsetLocalTime.getTimeInMillis());

        GregorianCalendar timeZonedCalendar = new GregorianCalendar();
        timeZonedCalendar.setTimeInMillis(offsetLocalTime.getTimeInMillis() + timeZoneCorrection);

        GregorianCalendar globalCalendar = new GregorianCalendar();
        globalCalendar.setTimeInMillis(timeZonedCalendar.getTimeInMillis() - timeZoneCorrection);

        suntimeLocal.setObserverTime(timeZonedCalendar);
        suntimeGlobal.setObserverTime(offsetLocalTime);

        long newJulianDayNumber = suntimeLocal.getJulianDayNumber();

        double phase = (suntimeGlobal.getJulianDate() - suntimeGlobal.getJulianDayNumber()) * 360;
        double tilt = -suntimeGlobal.getRealTimeDeclinationOfTheSun(Suntime.getJulianDate(offsetLocalTime));

        String yearString = ("0000" + offsetLocalTime.get(Calendar.YEAR));
        yearString = yearString.substring(yearString.length() - 4);
        String monthString = ("00" + (offsetLocalTime.get(Calendar.MONTH) + 1));
        monthString = monthString.substring(monthString.length() - 2);
        String dayString = ("00" + offsetLocalTime.get(Calendar.DAY_OF_MONTH));
        dayString = dayString.substring(dayString.length() - 2);
        String hourString = ("00" + offsetLocalTime.get(Calendar.HOUR_OF_DAY));
        hourString = hourString.substring(hourString.length() - 2);
        String minuteString = ("00" + offsetLocalTime.get(Calendar.MINUTE));
        minuteString = minuteString.substring(minuteString.length() - 2);
        String secondString = ("00" + offsetLocalTime.get(Calendar.SECOND));
        secondString = secondString.substring(secondString.length() - 2);
        String weekString = ("00" + offsetLocalTime.get(Calendar.WEEK_OF_YEAR));
        weekString = weekString.substring(weekString.length() - 2);

        // Update daily data only if it's a new day, or forced initialization event
        if (newJulianDayNumber != oldJulianDayNumber || initialize) {

            suntimeLocal.setObserverPosition(longitude, latitude);
            cetusNightList = cetustime.getNightList(timeZonedCalendar);

            double highNoonJulianDate = suntimeLocal.getHighnoonJulianDate();
            double sunriseJulianDate = suntimeLocal.getSunriseJulianDate();
            double sunsetJulianDate = suntimeLocal.getSunsetJulianDate();

            GregorianCalendar highNoonDate = Suntime.getCalendarDate(highNoonJulianDate, offsetLocalTime.getTimeZone());
            GregorianCalendar sunriseDate = Suntime.getCalendarDate(sunriseJulianDate, offsetLocalTime.getTimeZone());
            GregorianCalendar sunsetDate = Suntime.getCalendarDate(sunsetJulianDate, offsetLocalTime.getTimeZone());

            sundial.setHighNoon(highNoonDate);
            sundial.setHorizon(sunriseDate, sunsetDate);
            sundial.setCoordinates(longitude, latitude);
            sundial.setCetusTime(cetusNightList, timeZonedCalendar);
            sundial.setTimeZone(offsetLocalTime.getTimeZone());
        }

        sundial.setLocalTime(offsetLocalTime);
        sundial.updateCetusTimer(cetusNightList);
        sundial.updateDialMarkers();

        sundial.setGlobeDaylight(phase, tilt);

        sundial.getMatrixHour().setString(hourString);
        sundial.getMatrixMinute().setString(minuteString);
        sundial.getMatrixSecond().setString(secondString);
        sundial.getMatrixDay().setString(dayString);
        sundial.getMatrixMonth().setString(monthString);
        sundial.getMatrixYear().setString(yearString);
        sundial.getMatrixWeek().setString(weekString);

        updateSunchart(sunchart);
        updateDebugWindow(sundial);
    }

    private void resetGlobePosition(Sundial sundial, PositionType type) {

        if (type == PositionType.LONGITUDE) { longitude = Sunconfig.DEFAULT_LONGITUDE; }
        else if (type == PositionType.LATITUDE) { latitude = Sunconfig.DEFAULT_LATITUDE; }
        else if (type == PositionType.BOTH){
            longitude = customLongitude;
            latitude = customLatitude;
        }

        if (sundial.globeAnimationOnEh) {
            sundial.getLongitudeTimeline().setOnFinished(event -> initCurrentTime(sundial));
            sundial.rotateGlobeAnimated(longitude, latitude);
        } else {
            sundial.rotateGlobe(longitude, latitude);
            initCurrentTime(sundial);
        }
    }

    private void recordGlobePosition(Sundial sundial, PositionType type, MouseEvent event) {

        mouseButtonList.add(event.getButton());

        savedMouseX = event.getScreenX();
        savedMouseY = event.getScreenY();

        savedLongitude = longitude;
        savedLatitude = latitude;
    }

    private void rotateGlobe(Sundial sundial, PositionType positionType, DragEvent dragEvent) {

        if (positionType != PositionType.GOOGLE_MAPS) { return; }

        String string;

        if (dragEvent.getDragboard().hasString()) {
            string = dragEvent.getDragboard().getString();
        } else {
            return;
        }

        Pattern pattern = Pattern.compile(Sunconfig.GOOGLEMAPS_REGEX);
        Matcher matcher = pattern.matcher(string);

        if (matcher.matches()) {
            try {

                latitude = Double.parseDouble(matcher.group(1));
                longitude = Double.parseDouble(matcher.group(2));

                customLongitude = longitude;
                customLatitude = latitude;

            } catch (NumberFormatException e) {
                sundial.getInfoText().setText("Catasptrophic error while parsing coordinates!\nPlease don't try again.");
                sundial.moveGroup(sundial.getInfoTextGroup(), dragEvent);
                sundial.getInfoTextGroup().setVisible(true);
                debugErrorMessage = "NumberFormatException while parsing string: " + string + "\n" + e.getMessage();
            }
        } else {
            sundial.getInfoText().setText("Unable to match coordinates.\nPlease try again.");
            sundial.moveGroup(sundial.getInfoTextGroup(), dragEvent);
            sundial.getInfoTextGroup().setVisible(true);
        }

        if (sundial.getInfoTextGroup().isVisible()) {

            SleepTask sleepTask = new SleepTask(sundial);

            sleepTask.setOnSucceeded(refreshEvent -> sundial.getInfoTextGroup().setVisible(false));

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.execute(sleepTask);
            executorService.shutdown();
        }

        initCurrentTime(sundial);
        sundial.rotateGlobeAnimated(longitude, latitude);
    }

    private class SleepTask extends Task<Boolean> {

        Sundial sundial;

        public SleepTask(Sundial sundial) {
            this.sundial = sundial;
        }

        @Override
        protected Boolean call() {

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                debugTextArea.setText("Error while calling sleep for InfoText:\n" + e.getMessage());
            }

            return true;
        }
    }

    private void rotateGlobe(Sundial sundial, MouseEvent event) {

        if (!mouseButtonList.isEmpty()) {
            if(mouseButtonList.get(mouseButtonList.size() - 1).equals(MouseButton.MIDDLE)) {
                resetGlobePosition(sundial, PositionType.BOTH);
                return;
            }
        }

        double mouseScreenX = event.getScreenX();
        double mouseScreenY = event.getScreenY();

        double deltaLongitude = savedMouseX - mouseScreenX;
        double deltaLatitude = savedMouseY - mouseScreenY;

        savedMouseX = mouseScreenX;
        savedMouseY = mouseScreenY;

        double precision = 4;
        if (event.isSecondaryButtonDown()) { precision = 100; }

        longitude += round((deltaLongitude / precision) * 100) / 100d;
        latitude -= round((deltaLatitude / precision) * 100) / 100d;

        if (longitude < Suntime.MIN_LONGITUDE) { longitude = Suntime.MAX_LONGITUDE - (Suntime.MIN_LONGITUDE - longitude); }
        if (longitude > Suntime.MAX_LONGITUDE) { longitude = Suntime.MIN_LONGITUDE - (Suntime.MAX_LONGITUDE - longitude); }
        if (latitude < Suntime.MIN_LATITUDE) { latitude = Suntime.MIN_LATITUDE; }
        if (latitude > Suntime.MAX_LATITUDE) { latitude = Suntime.MAX_LATITUDE; }

        initCurrentTime(sundial);
        sundial.rotateGlobe(longitude, latitude);
    }

    private void rotateGlobe(Sundial sundial, PositionType positionType, MouseEvent event) {

        if (!mouseButtonList.isEmpty()) {
            if(mouseButtonList.get(mouseButtonList.size() - 1).equals(MouseButton.MIDDLE)) {
                resetGlobePosition(sundial, positionType);
                return;
            }
        }

        double mouseScreenX = event.getScreenX();
        double mouseScreenY = event.getScreenY();
        double deltaMouse;

        if (positionType == PositionType.LONGITUDE) {
            deltaMouse = savedMouseX - mouseScreenX;
        } else {
            deltaMouse = savedMouseY - mouseScreenY;
        }

        savedMouseX = mouseScreenX;
        savedMouseY = mouseScreenY;

        double precision = 4;
        if (event.isSecondaryButtonDown()) { precision = 100; }

        double delta = round((deltaMouse / precision) * 100) / 100d;

        if (positionType == PositionType.LONGITUDE) {
            longitude += delta;
        } else {
            latitude -= delta;
        }

        if (longitude < Suntime.MIN_LONGITUDE) { longitude = Suntime.MAX_LONGITUDE - (Suntime.MIN_LONGITUDE - longitude); }
        if (longitude > Suntime.MAX_LONGITUDE) { longitude = Suntime.MIN_LONGITUDE - (Suntime.MAX_LONGITUDE - longitude); }
        if (latitude < Suntime.MIN_LATITUDE) { latitude = Suntime.MIN_LATITUDE; }
        if (latitude > Suntime.MAX_LATITUDE) { latitude = Suntime.MAX_LATITUDE; }

        initCurrentTime(sundial);
        sundial.rotateGlobe(longitude, latitude);
    }

    private void rotateGlobe(Sundial sundial, PositionType type, ScrollEvent event) {

        if (!mouseButtonList.isEmpty()) {
            if(mouseButtonList.get(mouseButtonList.size() - 1).equals(MouseButton.MIDDLE)) {
                resetGlobePosition(sundial, type);
                return;
            }
        }

        double step = 0;

        if (event.getDeltaY() < 0) { step = -0.01; }
        if (event.getDeltaY() > 0) { step = 0.01; }

        if (type == PositionType.LONGITUDE) { longitude += step; }
        else { latitude += step; }

        if (longitude < Suntime.MIN_LONGITUDE) { longitude = Suntime.MAX_LONGITUDE - (Suntime.MIN_LONGITUDE - longitude); }
        if (longitude > Suntime.MAX_LONGITUDE) { longitude = Suntime.MIN_LONGITUDE - (Suntime.MAX_LONGITUDE - longitude); }
        if (latitude < Suntime.MIN_LATITUDE) { latitude = Suntime.MIN_LATITUDE; }
        if (latitude > Suntime.MAX_LATITUDE) { latitude = Suntime.MAX_LATITUDE; }

        initCurrentTime(sundial);
        sundial.rotateGlobe(longitude, latitude);
    }

    private void resetWindowSize(Stage stage, Group dialsGroup) {

        stage.setWidth(dialsGroup.getLayoutBounds().getWidth());
        stage.setHeight(dialsGroup.getLayoutBounds().getHeight());

    }

    private void recordCalendarPosition(Sundial sundial, MouseEvent event) {

        MouseButton mouseButton = event.getButton();
        mouseButtonList.add(event.getButton());

        if (mouseButton.equals(MouseButton.MIDDLE)) {
            resetTime(sundial);
            return;
        }

        savedMouseX = event.getScreenX();
        savedMouseY = event.getScreenY();

        offsetX = 0;
        offsetY = 0;

    }

    private void recordNightCompressionPosition(Sundial sundial, MouseEvent event) {

        MouseButton mouseButton = event.getButton();
        mouseButtonList.add(event.getButton());

        if (mouseButton.equals(MouseButton.MIDDLE)) {
            sundial.resetNightCompression();
            return;
        }

        savedMouseX = event.getScreenX();
        savedMouseY = event.getScreenY();

        offsetX = 0;
        offsetY = 0;

    }

    private void recordWindowPosition(Stage stage, MouseEvent event) {

        if (event != null) {

            mouseButtonList.add(event.getButton());

            savedMouseX = event.getScreenX();
            savedMouseY = event.getScreenY();
        }

        savedWindowPositionX = stage.getX();
        savedWindowPositionY = stage.getY();

        savedWindowSizeX = stage.getWidth();
        savedWindowSizeY = stage.getHeight();

        deltaX = stage.getX() - savedMouseX;
        deltaY = stage.getY() - savedMouseY;

        offsetX = 0;
        offsetY = 0;

        if (savedWindowPositionX + savedWindowSizeX / 2 - savedMouseX > 0 && savedWindowPositionY + savedWindowSizeY / 2 - savedMouseY > 0) { dX = -1; dY = -1; }
        if (savedWindowPositionX + savedWindowSizeX / 2 - savedMouseX < 0 && savedWindowPositionY + savedWindowSizeY / 2 - savedMouseY > 0) { dX = 1; dY = -1; }
        if (savedWindowPositionX + savedWindowSizeX / 2 - savedMouseX > 0 && savedWindowPositionY + savedWindowSizeY / 2 - savedMouseY < 0) { dX = -1; dY = 1; }
        if (savedWindowPositionX + savedWindowSizeX / 2 - savedMouseX < 0 && savedWindowPositionY + savedWindowSizeY / 2 - savedMouseY < 0) { dX = 1; dY = 1; }

    }

    private void recordWindowSize(Stage stage, Group dialsGroup, MouseEvent event) {

        if (event != null) {
            MouseButton mouseButton = event.getButton();
            mouseButtonList.add(event.getButton());

            if (mouseButton.equals(MouseButton.MIDDLE)) {
                resetWindowSize(stage, dialsGroup);
                return;
            }

            savedMouseX = event.getScreenX();
            savedMouseY = event.getScreenY();
        }

        savedWindowPositionX = stage.getX();
        savedWindowPositionY = stage.getY();

        savedWindowSizeX = stage.getWidth();
        savedWindowSizeY = stage.getHeight();

        deltaX = stage.getX() - savedMouseX;
        deltaY = stage.getY() - savedMouseY;

        offsetX = 0;
        offsetY = 0;

        if (savedWindowPositionX + savedWindowSizeX / 2 - savedMouseX > 0 && savedWindowPositionY + savedWindowSizeY / 2 - savedMouseY > 0) { dX = -1; dY = -1; }
        if (savedWindowPositionX + savedWindowSizeX / 2 - savedMouseX < 0 && savedWindowPositionY + savedWindowSizeY / 2 - savedMouseY > 0) { dX = 1; dY = -1; }
        if (savedWindowPositionX + savedWindowSizeX / 2 - savedMouseX > 0 && savedWindowPositionY + savedWindowSizeY / 2 - savedMouseY < 0) { dX = -1; dY = 1; }
        if (savedWindowPositionX + savedWindowSizeX / 2 - savedMouseX < 0 && savedWindowPositionY + savedWindowSizeY / 2 - savedMouseY < 0) { dX = 1; dY = 1; }

    }

    private void resizeWindow(Stage stage, Sundial sundial, MouseEvent event) {

        if (!mouseButtonList.isEmpty()) {
            if(mouseButtonList.get(mouseButtonList.size() - 1).equals(MouseButton.MIDDLE)) {
                return;
            }
        }

        double mouseX = event.getScreenX();
        double mouseY = event.getScreenY();

        double deltaSizeX = mouseX - savedMouseX;
        double deltaSizeY = mouseY - savedMouseY;

        double windowSizeX = savedWindowSizeX + deltaSizeX;
        double windowSizeY = savedWindowSizeY + deltaSizeY;

        double minWidth, minHeight, maxWidth, maxHeight;

        minWidth = Sunconfig.MIN_WIDTH;
        minHeight = Sunconfig.MIN_HEIGHT;

        Rectangle2D recCenterOfPointer = new Rectangle2D(savedWindowPositionX + savedWindowSizeX / 2, savedWindowPositionY + savedWindowSizeY / 2, 0, 0);

        if (Screen.getScreensForRectangle(recCenterOfPointer).size() > 0) {
            Rectangle2D currentScreen = Screen.getScreensForRectangle(recCenterOfPointer).get(0).getVisualBounds();
            maxWidth = currentScreen.getMaxX() - savedWindowPositionX;
            maxHeight = currentScreen.getMaxY() - savedWindowPositionY;
        } else {
            maxWidth = windowSizeX;
            maxHeight = windowSizeY;
        }

        if (event.isPrimaryButtonDown()) {
            if (maxWidth >= maxHeight) { maxWidth = maxHeight; }
            else { maxHeight = maxWidth; }
            if (windowSizeX >= windowSizeY) { windowSizeY = windowSizeX; }
            else { windowSizeX = windowSizeY; }
        }

        if (windowSizeX < minWidth) { windowSizeX = Sunconfig.MIN_WIDTH; }
        if (windowSizeY < minHeight) { windowSizeY = Sunconfig.MIN_HEIGHT; }
        if (windowSizeX > maxWidth) { windowSizeX = maxWidth; }
        if (windowSizeY > maxHeight) { windowSizeY = maxHeight; }

        stage.setWidth(windowSizeX);
        stage.setHeight(windowSizeY);

        updateDebugWindow(sundial);
    }


    private void toggleMaximizeWindow(Stage stage, MouseEvent event) {

        double maxWidth, maxHeight;
        double screenWidth, screenHeight;

        Rectangle2D currentScreen = getCurrentScreen(stage);
        if(currentScreen == null) { return; }

        screenWidth = currentScreen.getMaxX() - currentScreen.getMinX();
        screenHeight = currentScreen.getMaxY() - currentScreen.getMinY();

        if (maximizedEh) {

            stage.setX(savedWindowPositionX);
            stage.setY(savedWindowPositionY);

            stage.setWidth(savedWindowSizeX);
            stage.setHeight(savedWindowSizeY);

            maximizedEh = false;

        } else {
            savedWindowPositionX = stage.getX();
            savedWindowPositionY = stage.getY();

            savedWindowSizeX = stage.getWidth();
            savedWindowSizeY = stage.getHeight();

            maxWidth = min(screenWidth, screenHeight);
            maxHeight = min(screenWidth, screenHeight);

            stage.setX(currentScreen.getMaxX() - screenWidth / 2 - maxWidth / 2);
            stage.setY(currentScreen.getMaxY() - screenHeight / 2 - maxHeight / 2);

            stage.setWidth(maxWidth);
            stage.setHeight(maxHeight);

            maximizedEh = true;
        }

    }

    private void minimizeWindow(Stage stage, Timeline timeline, MouseEvent event) {

        if (!mouseButtonList.isEmpty()) {

            if (mouseButtonList.get(mouseButtonList.size() - 1).equals(MouseButton.SECONDARY)) {
                toggleDebugWindow(stage);
                return;
            }
        }

        stage.setIconified(true);
    }

    private Rectangle2D getCurrentScreen(Stage stage) {

        Rectangle2D currentScreen;

        Rectangle2D recCenterOfPointer = new Rectangle2D(stage.getX() + stage.getWidth() / 2, stage.getY() + stage.getHeight() / 2, 0, 0);

        if (Screen.getScreensForRectangle(recCenterOfPointer).size() > 0) {
            currentScreen = Screen.getScreensForRectangle(recCenterOfPointer).get(0).getVisualBounds();
        } else {
            return null;
        }

        return currentScreen;
    }

    private void toggleDebugWindow(Stage stage) {

        double x, y;

        Rectangle2D currentScreen = getCurrentScreen(stage);
        if (currentScreen == null) { x = 0; y = 0; }
        else {
            x = currentScreen.getMinX();
            y = currentScreen.getMinY();
        }

        if (debugWindow.isShowing()) {
            debugWindow.close();
        } else {
            debugWindow.setX(x);
            debugWindow.setY(y);
            debugWindow.show();
        }
    }

    private void toggleSunchartWindow(Stage stage) {

        double x, y;

        Rectangle2D currentScreen = getCurrentScreen(stage);
        if (currentScreen == null) { x = 0; y = 0; }
        else {
            x = currentScreen.getMinX();
            y = currentScreen.getMinY();
        }

        if (sunchartWindow.isShowing()) {
            sunchartWindow.close();
        } else {
            sunchartWindow.setX(x);
            sunchartWindow.setY(y);
            sunchartWindow.show();
        }
    }

    private void changeWindowPosition(Stage stage, MouseEvent event) {

        if (!mouseButtonList.isEmpty()) {
            if(!mouseButtonList.get(mouseButtonList.size() - 1).equals(MouseButton.PRIMARY)) { return; }
        }

        double winSizeX = stage.getWidth();
        double winSizeY = stage.getHeight();

        double mouseScreenX = event.getScreenX();
        double mouseScreenY = event.getScreenY();

        double positionX = mouseScreenX + deltaX;
        double positionY = mouseScreenY + deltaY;

        double centerPositionX = positionX + (winSizeX / 2.0);
        double centerPositionY = positionY + (winSizeY / 2.0);

        Rectangle2D recCenterOfPointer = new Rectangle2D(centerPositionX, centerPositionY, 0, 0);
        if (Screen.getScreensForRectangle(recCenterOfPointer).size() <= 0) { return; }

        Rectangle2D currentScreen = Screen.getScreensForRectangle(recCenterOfPointer).get(0).getVisualBounds();

        double currentScreenMinX = currentScreen.getMinX();
        double currentScreenMaxX = currentScreen.getMaxX();
        double currentScreenMinY = currentScreen.getMinY();
        double currentScreenMaxY = currentScreen.getMaxY();

        double newPositionX = positionX;
        double newPositionY = positionY;

        double screenCenterX = currentScreenMinX + (currentScreenMaxX - currentScreenMinX) / 2;
        double screenCenterY = currentScreenMinY + (currentScreenMaxY - currentScreenMinY) / 2;

        debugTextArea.setText(""
                + "\nnewPositionX = " + newPositionX
                + "\nnewPositionY = " + newPositionY
                + "\nscreenCenterX = " + screenCenterX
                + "\nscreenCenterY = " + screenCenterY
                + "\ncenterPositionX = " + centerPositionX
                + "\ncenterPositionY = " + centerPositionY
        );

        // snap to screen center
        if (snapToCenterEh) {

            if (abs(abs(screenCenterX) - abs(centerPositionX)) < Sunconfig.SNAP_TO_CENTER_RADIUS && abs(abs(screenCenterY) - abs(centerPositionY)) < Sunconfig.SNAP_TO_CENTER_RADIUS) {
                newPositionX = screenCenterX - winSizeX / 2;
                newPositionY = screenCenterY - winSizeY / 2;
            }
        }

        // stop at screen border
        if (positionX < currentScreenMinX)
            newPositionX = currentScreenMinX;
        if (positionX > (currentScreenMaxX - winSizeX))
            newPositionX = currentScreenMaxX - winSizeX;
        if (positionY < currentScreenMinY)
            newPositionY = currentScreenMinY;
        if (positionY > (currentScreenMaxY - winSizeY))
            newPositionY = currentScreenMaxY - winSizeY;

        stage.setX(newPositionX);
        stage.setY(newPositionY);
    }

    private void tinyGlobeAction(Sundial sundial, MouseEvent mouseEvent) {

        mouseButtonList.clear();

        // LMB action (toggle Globe)
        if (mouseEvent.getButton().equals(MouseButton.PRIMARY)) {
            sundial.toggleGlobeVisibility();
            return;
        }

        // RMB action (toggle Cetus time)
        if (mouseEvent.getButton().equals(MouseButton.SECONDARY)) {

            if (sundial.cetusTimeVisibleEh()) {
                sundial.setCetusTimeVisibility(false);
            } else {
                if (cetustime.cetusTimeExpiredEh()) {
                    refreshCetusTime(sundial, mouseEvent);
                }
                else {
                    showCetusTime(sundial, mouseEvent);
                }
            }

            return;
        }

        // MMB action (reset Coordinates)
        if (mouseEvent.getButton().equals(MouseButton.MIDDLE)) {
            resetGlobePosition(sundial, PositionType.BOTH);
            return;
        }

    }

    private void refreshCetusTime(Sundial sundial, MouseEvent mouseEvent) {

        refreshCetusDataTask = new RefreshCetusDataTask(cetustime);

        refreshCetusDataTask.setOnScheduled(refreshEvent -> {
            sundial.getInfoText().setText("Syncing with Cetus...");
            sundial.moveGroup(sundial.getInfoTextGroup(), mouseEvent);
            sundial.getInfoTextGroup().setVisible(true);
        });

        refreshCetusDataTask.setOnFailed(refreshEvent -> {
            sundial.getInfoText().setText("Failed to sync with Cetus.\nPlease try again.");
            sundial.moveGroup(sundial.getInfoTextGroup(), mouseEvent);
            sundial.getInfoTextGroup().setVisible(true);
        });

        refreshCetusDataTask.setOnSucceeded(refreshEvent -> {
            showCetusTime(sundial, mouseEvent);
            sundial.getInfoTextGroup().setVisible(false);
        });

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(refreshCetusDataTask);
        executorService.shutdown();
    }

    private void showCetusTime(Sundial sundial, MouseEvent mouseEvent) {

        if (cetustime.cetusTimeOkEh()) {

            if (cetusNightList.isEmpty()) { cetusNightList = cetustime.getNightList(offsetLocalTime); }

            sundial.setCetusTime(cetusNightList, offsetLocalTime);
            sundial.updateCetusTimer(cetusNightList);
            sundial.setCetusTimeVisibility(true);

        } else {
            sundial.getInfoText().setText("Cetus time unavailable: \n" + cetustime.getResult());
            sundial.moveGroup(sundial.getInfoTextGroup(), mouseEvent);
            sundial.getInfoTextGroup().setVisible(true);
        }

        if (sundial.getInfoTextGroup().isVisible()) {

            SleepTask sleepTask = new SleepTask(sundial);

            sleepTask.setOnSucceeded(hideInfoTextEvent -> sundial.getInfoTextGroup().setVisible(false));

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.execute(sleepTask);
            executorService.shutdown();
        }

        updateDebugWindow(sundial);
    }

    private class RefreshCetusDataTask extends Task<Boolean> {

        Cetustime cetustime;

        public RefreshCetusDataTask(Cetustime cetustime) {
            this.cetustime = cetustime;
        }

        @Override
        protected Boolean call() {
            this.cetustime.requestNewData();
            return cetustime.cetusTimeOkEh();
        }
    }

    private void setAlwaysOnTop(Stage stage, Sundial sundial, boolean alwaysOnTopEh) {
        stage.setAlwaysOnTop(alwaysOnTopEh);
        sundial.toggleAlwaysOnTop();
    }

    private void toggleAlwaysOnTop(Stage stage, Sundial sundial) {
        alwaysOnTopEh = !alwaysOnTopEh;
        setAlwaysOnTop(stage, sundial, alwaysOnTopEh);
    }

    private void updateDebugWindow(Sundial sundial) {

        if (!debugWindow.isShowing()) { return; }

        double dividend = sin(toRadians(-0.83d)) - sin(toRadians(latitude)) * sin(toRadians(suntimeLocal.getDeclinationOfTheSun()));
        double divisor = cos(toRadians(latitude)) * cos(toRadians(suntimeLocal.getDeclinationOfTheSun()));

        double julianDate = suntimeLocal.getJulianDate();
        double julianDayNumber = suntimeLocal.getJulianDayNumber();
        double highNoonJulianDate = suntimeLocal.getHighnoonJulianDate();
        double sunriseJulianDate = suntimeLocal.getSunriseJulianDate();
        double sunsetJulianDate = suntimeLocal.getSunsetJulianDate();
        double dayLength = sunsetJulianDate - sunriseJulianDate;

        GregorianCalendar highNoonDate = Suntime.getCalendarDate(highNoonJulianDate, offsetLocalTime.getTimeZone());
        GregorianCalendar sunriseDate = Suntime.getCalendarDate(sunriseJulianDate, offsetLocalTime.getTimeZone());
        GregorianCalendar sunsetDate = Suntime.getCalendarDate(sunsetJulianDate, offsetLocalTime.getTimeZone());

        StringBuilder cetusDataString = new StringBuilder();

        Iterator cetusDataMapIterator = cetustime.getDataMap().keySet().iterator();
        while (cetusDataMapIterator.hasNext()) {
            String key = (String) cetusDataMapIterator.next();
            cetusDataString.append(key).append(" = ").append(cetustime.getDataMap().get(key)).append("\n");
        }

        String cetusExpiryDate = ""
                + cetustime.getCetusExpiry().get(Calendar.HOUR_OF_DAY) + ":"
                + cetustime.getCetusExpiry().get(Calendar.MINUTE) + ":"
                + cetustime.getCetusExpiry().get(Calendar.SECOND)
                + " " + cetustime.getCetusExpiry().getTimeZone().getDisplayName()
                ;

        StringBuilder cetusNightListString = new StringBuilder();
        for(int i = 0; i < cetusNightList.size(); i++) {
            String nightStart = cetusNightList.get(i).get(0).getTime().toString();
            String nightEnd = cetusNightList.get(i).get(1).getTime().toString();
            cetusNightListString.append("\nnight ").append(i+1).append(": start = ").append(nightStart).append(", end = ").append(nightEnd);
        }

        long timeZoneOffset = offsetLocalTime.getTimeZone().getOffset(offsetLocalTime.getTimeInMillis());

        String timeZoneNumberString = "00" + abs(timeZoneOffset / (1000 * 60 * 60));
        timeZoneNumberString = timeZoneNumberString.substring(timeZoneNumberString.length() - 2);

        StringBuilder timeZoneString = new StringBuilder()
                .append("GMT")
                .append((timeZoneOffset < 0) ? "-" : "+")
                .append(timeZoneNumberString)
                ;

        String debugText = ""
                + "Day[9] date              : " + offsetLocalTime.getTime().toString() + "\n"
                + "Day[9] day of the year   : " + offsetLocalTime.get(Calendar.DAY_OF_YEAR) + "\n"
                + "Day[9] Julian Date       : " + Sunconfig.julianDateFormat.format(julianDate) + " (UTC)" + "\n"
                + "Day[9] Gregorian Date    : " + Suntime.getCalendarDate(julianDate, offsetLocalTime.getTimeZone()).getTime().toString() + "\n"
                + "Day[9] Julian Day Number : " + julianDayNumber + "\n"
                + "TimeZone String : " + timeZoneString + "\n"
                + "High Noon  : " + highNoonDate.getTime().toString() + "\n"
                + "Sunrise    : " + sunriseDate.getTime().toString() + "\n"
                + "Sunset     : " + sunsetDate.getTime().toString() + "\n"
                + "Day Length : " + Suntime.printSecondsToTime(Suntime.convertFractionToSeconds(dayLength)) + "\n"
                + "meanAnomaly = " + suntimeLocal.getMeanAnomaly() + "\n"
                + "equationOfCenter = " + suntimeLocal.getEquationOfCenter() + "\n"
                + "eclipticalLongitude = " + suntimeLocal.getEclipticalLongitude() + "\n"
                + "rightAscension = " + suntimeLocal.getRightAscension() + "\n"
                + "declinationOfTheSun = " + suntimeLocal.getDeclinationOfTheSun() + "\n"
                + "siderealTime = " + suntimeLocal.getSiderealTime() + "\n"
                + "hourAngle = " + suntimeLocal.getHourAngle() + "\n"
                + "solarTransit = " + suntimeLocal.getSolarTransit() + "\n"
                + "localHourAngle = " + suntimeLocal.getLocalHourAngle() + "\n"
                + "localHourAngle dividend = " + dividend + "\n"
                + "localHourAngle divisor = " + divisor + "\n"
                + "longitude = " + longitude + "\n"
                + "latitude = " + latitude + "\n"
                + "Tx =  " + sundial.getDialHighNoonGroup().getLocalToParentTransform().getTx() + "\n"
                + "Ty =  " + sundial.getDialHighNoonGroup().getLocalToParentTransform().getTy() + "\n"
                + "Mxx = " + sundial.getDialHighNoonGroup().getLocalToParentTransform().getMxx() + "\n"
                + "Mxy = " + sundial.getDialHighNoonGroup().getLocalToParentTransform().getMxy() + "\n"
                + "Myx = " + sundial.getDialHighNoonGroup().getLocalToParentTransform().getMyx() + "\n"
                + "Myy = " + sundial.getDialHighNoonGroup().getLocalToParentTransform().getMyy() + "\n"
//                + "Cetus nightList = " + cetusNightListString + "\n"
                + "Cetus okEh = " + cetustime.cetusTimeOkEh() + "\n"
                + "Cetus result = " + cetustime.getResult() + "\n"
                + "Cetus data expired = " + cetustime.cetusTimeExpiredEh() + "\n"
//                + "Cetus isDay = " + cetustime.cetusDayEh() + "\n"
                + "Cetus expiry calendar = " + cetustime.getCetusExpiry().getTime() + "\n"
//                + "Cetus expiry string = " + cetusExpiryDate + "\n"
                + "Cetus reloadCounter: " + cetustime.getReloadCounter() + "\n"
                + "Cetus dataMap: \n" + cetusDataString + "\n"
                ;

        if (debugErrorMessage != null && !debugErrorMessage.isEmpty()) {
            debugText = debugErrorMessage + "\n" + debugText;
        }

        debugTextArea.setText(debugText);
    }

    private void updateSunchart(Sunchart sunchart) {

        if (sunchartWindow.isShowing()) {

            if (
                    mouseButtonList.isEmpty() &&
                        (
                        longitude != sunchart.getLongitude() ||
                        latitude != sunchart.getLatitude() ||
                        offsetLocalTime.get(Calendar.YEAR) != sunchart.getYear()
                        )
                    ) {

                sunchart.setSpacetimePosition(longitude, latitude, offsetLocalTime.get(Calendar.YEAR));
                sunchart.updateChartData();
            }
        }
    }

}
