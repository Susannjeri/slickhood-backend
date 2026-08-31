package org.pms.silverocean.service.insurance;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pms.silverocean.service.insurance.InsuranceModels.InsurerEmailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

@Component @RequiredArgsConstructor @Slf4j
public class InsuranceMailboxPoller {
 private static final Pattern CORRELATION=Pattern.compile("(?i)Correlation ID:\\s*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
 private static final int MAX_BODY=1_000_000;
 private final InsuranceCorrespondenceService correspondence;
 @Value("${app.insurance.imap.enabled:${INSURANCE_IMAP_ENABLED:false}}") private boolean enabled;
 @Value("${app.insurance.imap.host:${INSURANCE_IMAP_HOST:mail.silverwoodinsurance.com}}") private String host;
 @Value("${app.insurance.imap.port:${INSURANCE_IMAP_PORT:993}}") private int port;
 @Value("${app.insurance.imap.username:${INSURANCE_IMAP_USERNAME:info@silverwoodinsurance.com}}") private String username;
 @Value("${app.insurance.imap.password:${INSURANCE_IMAP_PASSWORD:}}") private String password;
 @Value("${app.insurance.imap.max-per-poll:50}") private int maxPerPoll;

 @Scheduled(fixedDelayString="${app.insurance.imap.poll-delay-ms:60000}")
 public void poll(){
  if(!enabled)return;
  if(password==null||password.isBlank()){log.error("Insurance mailbox polling is enabled but no IMAP password is configured");return;}
  Properties props=new Properties();props.put("mail.store.protocol","imaps");props.put("mail.imaps.ssl.enable","true");props.put("mail.imaps.connectiontimeout","10000");props.put("mail.imaps.timeout","15000");
  try(Store store=Session.getInstance(props).getStore("imaps")){store.connect(host,port,username,password);try(Folder inbox=store.getFolder("INBOX")){inbox.open(Folder.READ_WRITE);Message[] unseen=inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN),false));int start=Math.max(0,unseen.length-Math.max(1,Math.min(200,maxPerPoll)));for(int i=start;i<unseen.length;i++)process(unseen[i]);}}
  catch(Exception e){log.error("Insurance mailbox poll failed: {}",safe(e.getMessage()));}
 }
 private void process(Message message){try{String body=text(message.getContent(),0);Matcher matcher=CORRELATION.matcher(body);if(!matcher.find()){log.warn("Insurance mailbox message ignored because it has no Silverwood correlation ID");return;}String from=message.getFrom()!=null&&message.getFrom().length>0?address(message.getFrom()[0]):"";String subject=Objects.toString(message.getSubject(),"(no subject)");String external=header(message,"Message-ID");if(external==null)external="mailbox:"+hash(from+"|"+subject+"|"+Objects.toString(message.getSentDate(),"")+"|"+body);correspondence.recordMailboxResponse(new InsurerEmailResponse(matcher.group(1),from,subject,body,external));message.setFlag(Flags.Flag.SEEN,true);}catch(Exception e){log.warn("Insurance mailbox message retained for retry: {}",safe(e.getMessage()));}}
 private String text(Object content,int depth)throws Exception{if(depth>5||content==null)return "";if(content instanceof String s)return limit(s);if(content instanceof Multipart m){StringBuilder out=new StringBuilder();for(int i=0;i<m.getCount()&&out.length()<MAX_BODY;i++){BodyPart p=m.getBodyPart(i);String disposition=p.getDisposition();if(Part.ATTACHMENT.equalsIgnoreCase(disposition))continue;out.append(text(p.getContent(),depth+1)).append('\n');}return limit(out.toString());}return "";}
 private String address(Address a){return a instanceof InternetAddress ia?ia.getAddress():a.toString();}
 private String header(Message m,String name)throws MessagingException{String[] h=m.getHeader(name);return h==null||h.length==0?null:h[0];}
 private String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private String limit(String s){return s.length()>MAX_BODY?s.substring(0,MAX_BODY):s;}
 private String safe(String s){if(s==null)return "unknown";return s.replaceAll("[\\r\\n]"," ").substring(0,Math.min(300,s.length()));}
}
