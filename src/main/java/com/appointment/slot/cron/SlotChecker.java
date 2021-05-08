package com.appointment.slot.cron;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.appointment.slot.vo.CentersVO;
import com.appointment.slot.vo.CentreVO;
import com.appointment.slot.vo.SessionVO;

@Service
public class SlotChecker {

	private static final Log logger = LogFactory.getLog(SlotChecker.class);

	@Autowired
	private Environment environment;

	@Autowired
	private JavaMailSender mailSender;

	@PostConstruct
	void init() {
		this.checkAppointmentDetails();
	}

	@Scheduled(cron = "0 0/5 * * * *")
	public void checkAppointmentDetails() {
		try {
			String date = DateTimeFormatter.ofPattern("dd-MM-yyyy").format(LocalDateTime.now());
			String url = "https://cdn-api.co-vin.in/api/v2/appointment/sessions/public/calendarByDistrict?district_id="
					+ environment.getProperty("districtId") + "&date=" + date;
			UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
			HttpHeaders headers = new HttpHeaders();
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
			headers.add("user-agent", "aqswdefrgthyjukil");
			HttpEntity<String> httpEntity = new HttpEntity<>(headers);
			CloseableHttpClient httpClient = HttpClients.custom().setSSLHostnameVerifier(new NoopHostnameVerifier())
					.build();
			HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
			requestFactory.setHttpClient(httpClient);
			RestTemplate restTemplate = new RestTemplate(requestFactory);
			ResponseEntity<CentersVO> response = restTemplate.exchange(builder.build().encode().toUri(), HttpMethod.GET,
					httpEntity, CentersVO.class);

			if (Objects.nonNull(response.getBody())) {
				CentersVO centersVO = response.getBody();
				if (!CollectionUtils.isEmpty(centersVO.getCenters())) {
					List<CentreVO> centersList = centersVO.getCenters();

					centersList = centersList.stream().filter(centre -> centre.getSessions().stream().allMatch(
							session -> session.getMin_age_limit() == Long.parseLong(environment.getProperty("age"))
									&& session.getAvailable_capacity() > 0))
							.collect(Collectors.toList());
					if (!CollectionUtils.isEmpty(centersList)) {
						sendEmail(centersList);
						loggger.info("Sending Email");
					}
				}
			}
		} catch (MessagingException | IOException e) {
			logger.error(e.getMessage());
		}

	}

	public void sendEmail(List<CentreVO> centres) throws MessagingException, UnsupportedEncodingException {
		final StringBuilder body = new StringBuilder();
		centres.forEach(center -> {
			body.append("<table border=\"1\">");
			body.append("<tr><th>Name</th><td>").append(center.getName()).append("</td></tr>")
					.append("<tr><th>Address</th><td>").append(center.getAddress()).append("</td></tr>")
					.append("<tr><th>State Name</th><td>").append(center.getState_name()).append("</td></tr>")
					.append("<tr><th>District Name</th><td>").append(center.getDistrict_name()).append("</td></tr>")
					.append("<tr><th>Block Name</th><td>").append(center.getBlock_name()).append("</td></tr>")
					.append("<tr><th>Pin code</th><td>").append(center.getPincode()).append("</td></tr>")
					.append("<tr><th>Fee Type</th><td>").append(center.getFee_type()).append("</td></tr></table>");
			body.append("<br>");
			List<SessionVO> sessionDTOList = center.getSessions();
			body.append(
					"<table  border=\"1\"><tr><th>Date</th><th>Available Capacity</th><th>Minimum Age Limit</th><th>Vaccine</th></tr>");
			for (SessionVO sessionDTO : sessionDTOList) {
				body.append("<tr><td>").append(sessionDTO.getDate()).append("</td><td>")
						.append(sessionDTO.getAvailable_capacity()).append("</td><td>")
						.append(+sessionDTO.getMin_age_limit()).append("</td><td>").append(sessionDTO.getVaccine())
						.append("</td></tr>");
			}
			body.append("</table>");
			body.append("<br>");
			body.append(
					"=================================================================================================================<br>");
		});
		MimeMessage mimeMessage = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
		helper.setFrom(
				new InternetAddress(environment.getProperty("spring.mail.username"), "Covid Vaccine Slot Available"));
		helper.setTo(environment.getProperty("receiverMailId"));
		helper.setText(body.toString(), true);
		String time = DateTimeFormatter.ofPattern("dd-MM-yyyy h:mm a").format(LocalDateTime.now());
		helper.setSubject("Slots Available now " + time);
		mailSender.send(mimeMessage);

	}

}
