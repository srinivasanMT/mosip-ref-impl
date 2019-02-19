package io.mosip.registration.controller.device;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.reg.RegistrationController;
import io.mosip.registration.controller.reg.UserOnboardParentController;
import io.mosip.registration.dto.biometric.BiometricDTO;
import io.mosip.registration.dto.demographic.ApplicantDocumentDTO;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

@Controller
public class FaceCaptureController extends BaseController implements Initializable {

	/**
	 * Instance of {@link Logger}
	 */
	private static final Logger LOGGER = AppConfig.getLogger(FaceCaptureController.class);

	@FXML
	private Button takePhoto;

	private Pane selectedPhoto;
	@FXML
	private Pane applicantImagePane;
	@FXML
	private ImageView applicantImage;
	@FXML
	private Pane exceptionImagePane;
	@FXML
	private ImageView exceptionImage;
	@FXML
	public Button biometricPrevBtn;
	@FXML
	public Button saveBiometricDetailsBtn;

	@Autowired
	private RegistrationController registrationController;

	@Autowired
	private WebCameraController webCameraController;

	@Autowired
	private UserOnboardParentController userOnboardParentController;

	@Value("${capture_photo_using_device}")
	public String capturePhotoUsingDevice;

	private Timestamp lastPhotoCaptured;

	private Timestamp lastExceptionPhotoCaptured;

	@FXML
	private Label photoAlert;

	private BufferedImage applicantBufferedImage;
	private BufferedImage exceptionBufferedImage;
	private Image defaultImage;
	private Image defaultExceptionImage;
	private boolean applicantImageCaptured;
	private boolean exceptionImageCaptured;

	private Boolean toggleBiometricException = null;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		LOGGER.info("REGISTRATION - UI - FACE_CAPTURE_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
				"Loading of FaceCapture screen started");

		takePhoto.setDisable(true);
		defaultExceptionImage = new Image(getClass().getResourceAsStream("/images/ExceptionPhoto.png"));
		exceptionImage.setImage(defaultExceptionImage);
		if (capturePhotoUsingDevice.equals(RegistrationConstants.ENABLE)
				|| (boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
			// for applicant biometrics
			if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {

				if (getBiometricDTOFromSession() != null && getBiometricDTOFromSession().getOperatorBiometricDTO()
						.getFaceDetailsDTO().getFace() != null) {
					applicantImage.setImage(convertBytesToImage(
							getBiometricDTOFromSession().getOperatorBiometricDTO().getFaceDetailsDTO().getFace()));
				} else {
					initialize();
				}
			} else {
				if (getRegistrationDTOFromSession() != null
						&& getRegistrationDTOFromSession().getDemographicDTO().getApplicantDocumentDTO() != null) {
					if (getRegistrationDTOFromSession().getDemographicDTO().getApplicantDocumentDTO()
							.getPhoto() != null) {
						byte[] photoInBytes = getRegistrationDTOFromSession().getDemographicDTO()
								.getApplicantDocumentDTO().getPhoto();
						if (photoInBytes != null) {
							ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(photoInBytes);
							applicantImage.setImage(new Image(byteArrayInputStream));
						}
					}
					if (getRegistrationDTOFromSession().getDemographicDTO().getApplicantDocumentDTO()
							.getExceptionPhoto() != null) {
						byte[] exceptionPhotoInBytes = getRegistrationDTOFromSession().getDemographicDTO()
								.getApplicantDocumentDTO().getExceptionPhoto();
						if (exceptionPhotoInBytes != null) {
							ByteArrayInputStream inputStream = new ByteArrayInputStream(exceptionPhotoInBytes);
							exceptionImage.setImage(new Image(inputStream));
						}
					}
				} else {
					initialize();
				}
			}
		}
	}

	private void initialize() {
		defaultImage = applicantImage.getImage();
		defaultExceptionImage = exceptionImage.getImage();
		applicantImageCaptured = false;
		exceptionImageCaptured = false;
		exceptionBufferedImage = null;
	}

	/**
	 * 
	 * To open camera for the type of image that is to be captured
	 * 
	 * @param imageType
	 *            type of image that is to be captured
	 */
	private void openWebCamWindow(String imageType) {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Opening WebCamera to capture photograph");
		if (webCameraController.isWebcamPluggedIn()) {
			try {
				Stage primaryStage = new Stage();
				primaryStage.initStyle(StageStyle.UNDECORATED);
				FXMLLoader loader = BaseController
						.loadChild(getClass().getResource(RegistrationConstants.WEB_CAMERA_PAGE));
				Parent webCamRoot = loader.load();

				WebCameraController cameraController = loader.getController();
				cameraController.init(this, imageType);
				Scene scene = new Scene(webCamRoot);
				ClassLoader classLoader = ClassLoader.getSystemClassLoader();
				scene.getStylesheets()
						.add(classLoader.getResource(RegistrationConstants.CSS_FILE_PATH).toExternalForm());
				primaryStage.setScene(scene);
				primaryStage.initModality(Modality.WINDOW_MODAL);
				primaryStage.initOwner(fXComponents.getStage());
				primaryStage.show();
			} catch (IOException ioException) {
				LOGGER.error(RegistrationConstants.REGISTRATION_CONTROLLER, APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID,
						ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
			}
		} else {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.WEBCAM_ALERT_CONTEXT);
		}
	}

	/**
	 * To save the captured applicant biometrics to the DTO
	 */
	@FXML
	private void saveBiometricDetails() {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "saving the details of applicant biometrics");
		if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
			if (validateOperatorPhoto()) {
				registrationController.saveBiometricDetails(applicantBufferedImage, exceptionBufferedImage);
				if (getBiometricDTOFromSession().getOperatorBiometricDTO().getFaceDetailsDTO().getFace() != null) {
					userOnboardParentController.showCurrentPage(RegistrationConstants.FACE_CAPTURE,
							getOnboardPageDetails(RegistrationConstants.FACE_CAPTURE, RegistrationConstants.NEXT));
				}
			}
		} else {
			if (validateApplicantImage()) {
				registrationController.saveBiometricDetails(applicantBufferedImage, exceptionBufferedImage);
			}
		}
	}

	@FXML
	private void goToPreviousPane() {
		if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
			if (validateOperatorPhoto()) {
				registrationController.saveBiometricDetails(applicantBufferedImage, exceptionBufferedImage);
				if (getBiometricDTOFromSession().getOperatorBiometricDTO().getFaceDetailsDTO().getFace() != null) {
					userOnboardParentController.showCurrentPage(RegistrationConstants.FACE_CAPTURE,
							getOnboardPageDetails(RegistrationConstants.FACE_CAPTURE, RegistrationConstants.PREVIOUS));
				}
			}

		} else {

			try {
				if (getRegistrationDTOFromSession().getSelectionListDTO() != null) {
					SessionContext.map().put("faceCapture", false);

					if (getRegistrationDTOFromSession().getSelectionListDTO().isBiometricIris()
							&& getRegistrationDTOFromSession().getSelectionListDTO().isBiometricFingerprint()
							|| getRegistrationDTOFromSession().getSelectionListDTO().isBiometricIris()
							|| !getRegistrationDTOFromSession().getBiometricDTO().getApplicantBiometricDTO()
									.getIrisDetailsDTO().isEmpty()) {
						SessionContext.map().put("irisCapture", true);
					} else if (getRegistrationDTOFromSession().getSelectionListDTO().isBiometricFingerprint()
							|| !getRegistrationDTOFromSession().getBiometricDTO().getApplicantBiometricDTO()
									.getFingerprintDetailsDTO().isEmpty()) {
						SessionContext.map().put("fingerPrintCapture", true);
					} else if (!getRegistrationDTOFromSession().getSelectionListDTO().isBiometricFingerprint()
							&& !getRegistrationDTOFromSession().getSelectionListDTO().isBiometricIris()) {
						SessionContext.map().put("documentScan", true);
					}
				}
				registrationController.showCurrentPage(RegistrationConstants.FACE_CAPTURE,
						getPageDetails(RegistrationConstants.FACE_CAPTURE, RegistrationConstants.PREVIOUS));

			} catch (RuntimeException runtimeException) {
				LOGGER.error("REGISTRATION - COULD NOT GO TO DEMOGRAPHIC TITLE PANE ", APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID,
						runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
			}
		}
	}

	/**
	 * 
	 * To set the captured image to the imageView in the Applicant Biometrics page
	 * 
	 * @param capturedImage
	 *            the image that is captured
	 * @param photoType
	 *            the type of image whether exception image or applicant image
	 */
	@Override
	public void saveApplicantPhoto(BufferedImage capturedImage, String photoType) {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Opening WebCamera to capture photograph");

		if (photoType.equals(RegistrationConstants.APPLICANT_IMAGE)) {

			/* Set Time which last photo was captured */
			lastPhotoCaptured = getCurrentTimestamp();

			Image capture = SwingFXUtils.toFXImage(capturedImage, null);
			applicantImage.setImage(capture);
			applicantBufferedImage = capturedImage;
			applicantImageCaptured = true;
			try {
				if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
					ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
					ImageIO.write(applicantBufferedImage, RegistrationConstants.WEB_CAMERA_IMAGE_TYPE,
							byteArrayOutputStream);
					byte[] photoInBytes = byteArrayOutputStream.toByteArray();
					((BiometricDTO) SessionContext.map().get(RegistrationConstants.USER_ONBOARD_DATA))
							.getOperatorBiometricDTO().getFaceDetailsDTO().setFace(photoInBytes);
				}
			} catch (Exception ioException) {
				LOGGER.error(RegistrationConstants.REGISTRATION_CONTROLLER, APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID,
						ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
			}
		} else if (photoType.equals(RegistrationConstants.EXCEPTION_IMAGE)) {

			/* Set Time which last Exception photo was captured */
			lastExceptionPhotoCaptured = getCurrentTimestamp();
			Image capture = SwingFXUtils.toFXImage(capturedImage, null);
			exceptionImage.setImage(capture);
			exceptionBufferedImage = capturedImage;
			exceptionImageCaptured = true;
		}
	}

	/**
	 * 
	 * To clear the captured image from the imageView in the Applicant Biometrics
	 * page
	 *
	 * @param photoType
	 *            the type of image that is to be cleared, whether exception image
	 *            or applicant image
	 */
	@Override
	public void clearPhoto(String photoType) {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "clearing the image that is captured");

		if (photoType.equals(RegistrationConstants.APPLICANT_IMAGE) && applicantBufferedImage != null) {
			applicantImage.setImage(defaultImage);
			applicantBufferedImage = null;
			applicantImageCaptured = false;
		} else if (photoType.equals(RegistrationConstants.EXCEPTION_IMAGE) && exceptionBufferedImage != null) {
			exceptionImage.setImage(defaultExceptionImage);
			exceptionBufferedImage = null;
			exceptionImageCaptured = false;
		}
	}

	/**
	 * To validate the applicant image while going to next section
	 */
	private boolean validateApplicantImage() {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "validating applicant biometrics");

		toggleBiometricException = (Boolean) SessionContext.userContext().getUserMap()
				.get(RegistrationConstants.TOGGLE_BIO_METRIC_EXCEPTION);

		boolean imageCaptured = false;
		if (applicantImageCaptured) {
			if (toggleBiometricException) {
				if (exceptionImageCaptured) {
					if (getRegistrationDTOFromSession() != null
							&& getRegistrationDTOFromSession().getDemographicDTO() != null) {
						imageCaptured = true;
					} else {
						generateAlert(RegistrationConstants.ERROR,
								RegistrationUIConstants.DEMOGRAPHIC_DETAILS_ERROR_CONTEXT);
					}
				} else {
					generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.APPLICANT_IMAGE_ERROR);
				}
			} else {
				if (getRegistrationDTOFromSession() != null
						&& getRegistrationDTOFromSession().getDemographicDTO() != null) {
					imageCaptured = true;
				} else {
					generateAlert(RegistrationConstants.ERROR,
							RegistrationUIConstants.DEMOGRAPHIC_DETAILS_ERROR_CONTEXT);
				}
			}
		} else {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.APPLICANT_IMAGE_ERROR);
		}
		return imageCaptured;
	}

	private boolean validateOperatorPhoto() {
		if (getBiometricDTOFromSession().getOperatorBiometricDTO().getFaceDetailsDTO().getFace() != null) {
			return true;
		} else {
			generateAlert(RegistrationConstants.ERROR, "Please capture the photo");
			return false;
		}
	}

	public void clearExceptionImage() {
		exceptionBufferedImage = null;
		exceptionImage.setImage(defaultExceptionImage);
		ApplicantDocumentDTO applicantDocumentDTO = getRegistrationDTOFromSession().getDemographicDTO()
				.getApplicantDocumentDTO();
		if (applicantDocumentDTO != null && applicantDocumentDTO.getExceptionPhoto() != null) {
			applicantDocumentDTO.setExceptionPhoto(null);
			if (applicantDocumentDTO.getExceptionPhotoName() != null) {
				applicantDocumentDTO.setExceptionPhotoName(null);
			}
			applicantDocumentDTO.setHasExceptionPhoto(false);
		}
	}

	@FXML
	private void enableCapture(MouseEvent mouseEvent) {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Enabling the capture button based on selected pane");

		boolean hasBiometricException = false;
		if (((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER))
				&& !getBiometricDTOFromSession().getOperatorBiometricDTO().getBiometricExceptionDTO().isEmpty()) {
			hasBiometricException = true;
		} else {
			hasBiometricException = (Boolean) SessionContext.userContext().getUserMap()
					.get(RegistrationConstants.TOGGLE_BIO_METRIC_EXCEPTION);
		}
		Pane sourcePane = (Pane) mouseEvent.getSource();
		sourcePane.requestFocus();
		selectedPhoto = sourcePane;
		takePhoto.setDisable(true);
		if (selectedPhoto.getId().equals(RegistrationConstants.APPLICANT_PHOTO_PANE)
				|| (selectedPhoto.getId().equals(RegistrationConstants.EXCEPTION_PHOTO_PANE)
						&& hasBiometricException)) {
			takePhoto.setDisable(false);
		}
	}

	/**
	 * To open webcam window to capture image for selected type
	 */
	@FXML
	private void takePhoto() {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Opening Webcam Window to capture Image");

		int configuredSecondsForRecapture = Integer
				.parseInt((String) ApplicationContext.map().get(RegistrationConstants.RECAPTURE_TIME));

		if (selectedPhoto.getId().equals(RegistrationConstants.APPLICANT_PHOTO_PANE)) {
			if (webCameraController.webCameraPane == null
					|| !(webCameraController.webCameraPane.getScene().getWindow().isShowing())) {
				if (validatePhotoTimer(lastPhotoCaptured, configuredSecondsForRecapture, photoAlert)) {
					openWebCamWindow(RegistrationConstants.APPLICANT_IMAGE);
				} else {
					takePhoto.setDisable(true);
				}
			}
		} else if (selectedPhoto.getId().equals(RegistrationConstants.EXCEPTION_PHOTO_PANE)) {
			if (webCameraController.webCameraPane == null
					|| !(webCameraController.webCameraPane.getScene().getWindow().isShowing())) {

				if (validatePhotoTimer(lastExceptionPhotoCaptured, configuredSecondsForRecapture, photoAlert)) {
					openWebCamWindow(RegistrationConstants.EXCEPTION_IMAGE);
				} else {
					takePhoto.setDisable(true);
				}
			}
		}
	}

	/**
	 * To validate the time of last capture to allow re-capture
	 * 
	 * @param lastPhoto
	 *            the timestamp when last photo is captured
	 * @param configuredSecs
	 *            the configured number of seconds for re-capture
	 * @param photoLabel
	 *            the label to show the timer for re-capture
	 * @return boolean returns true if recapture is allowed
	 */
	private boolean validatePhotoTimer(Timestamp lastPhoto, int configuredSecs, Label photoLabel) {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Validating time to allow re-capture");

		if (lastPhoto == null) {
			return true;
		}
		/* Get Calendar instance */
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Timestamp(System.currentTimeMillis()));
		cal.add(Calendar.SECOND, -configuredSecs);

		int diffSeconds = Seconds.secondsBetween(new DateTime(lastPhoto.getTime()), DateTime.now()).getSeconds();
		if (diffSeconds >= configuredSecs) {
			return true;
		} else {
			setTimeLabel(photoLabel, configuredSecs, diffSeconds);
			return false;
		}
	}

	/**
	 * To set the label that displays time left to re-capture
	 * 
	 * @param photoLabel
	 *            the label to show the timer for re-capture
	 * @param configuredSecs
	 *            the configured number of seconds for re-capture
	 * @param diffSeconds
	 *            the difference between last captured time and present time
	 */
	private void setTimeLabel(Label photoLabel, int configuredSecs, int diffSeconds) {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Setting label to display time to recapture");

		SimpleIntegerProperty timeDiff = new SimpleIntegerProperty((Integer) (configuredSecs - diffSeconds));

		photoLabel.setVisible(true);
		// Bind the photoLabel text property to the timeDiff property
		photoLabel.textProperty().bind(Bindings.concat("Recapture after ", timeDiff.asString(), " seconds"));
		Timeline timeline = new Timeline();
		timeline.getKeyFrames().add(
				new KeyFrame(Duration.seconds((Integer) (configuredSecs - diffSeconds)), new KeyValue(timeDiff, 1)));
		timeline.setOnFinished(event -> {
			takePhoto.setDisable(false);
			photoLabel.setVisible(false);
		});
		timeline.play();
	}

	private BiometricDTO getBiometricDTOFromSession() {
		return (BiometricDTO) SessionContext.map().get(RegistrationConstants.USER_ONBOARD_DATA);
	}

	/**
	 * To get the current timestamp
	 * 
	 * @return Timestamp returns the current timestamp
	 */
	private Timestamp getCurrentTimestamp() {
		return Timestamp.from(Instant.now());
	}
}
