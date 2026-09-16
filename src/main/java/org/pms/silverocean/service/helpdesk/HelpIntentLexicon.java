package org.pms.silverocean.service.helpdesk;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Shared support vocabulary. It maps the ordinary ways customers describe a
 * journey to one stable search term without changing the meaning of approved
 * help articles or granting the AI access to private records.
 */
final class HelpIntentLexicon {
    private HelpIntentLexicon() {}

    private record Phrase(String variant, String canonical, Pattern pattern) {}
    private static final Map<String, String> WORDS;
    private static final List<Phrase> PHRASES;

    static {
        Map<String, String> words = new HashMap<>();
        List<Phrase> phrases = new ArrayList<>();

        family(words, phrases, "accountlogin",
                "login", "signin", "log in", "log on", "sign in", "access my account", "account access",
                "get into my account", "enter my account", "cant log in", "cannot log in", "unable to log in",
                "cant sign in", "cannot sign in", "unable to sign in");
        family(words, phrases, "accountlogout", "logout", "signout", "log out", "sign out", "leave my account");
        family(words, phrases, "accountregister", "register", "registration", "registering", "registered", "signup", "sign up", "create an account",
                "create account", "new account", "join slickhood");
        family(words, phrases, "passwordreset", "password reset", "reset password", "forgot password", "forgotten password",
                "change password", "recover account", "recover my account");
        family(words, phrases, "identityverification", "kyc", "identity verification", "verify identity", "know your customer",
                "proof of identity", "identity documents", "verification documents");
        family(words, phrases, "onetimepassword", "otp", "one time password", "verification code", "security code", "passcode");
        family(words, phrases, "verify", "verification", "verified", "verifying");
        family(words, phrases, "start", "started", "starting");

        family(words, phrases, "create", "create", "add", "set up", "setup");
        family(words, phrases, "update", "update", "edit", "modify", "amend", "correct details", "change details");
        family(words, phrases, "delete", "delete", "remove", "erase");
        family(words, phrases, "view", "view", "show", "display", "see details", "open details");
        family(words, phrases, "find", "find", "search", "locate", "look for");
        family(words, phrases, "upload", "upload", "attach", "add attachment", "attach document");
        family(words, phrases, "download", "download", "export", "save a copy");
        family(words, phrases, "activate", "activate", "enable", "turn on");
        family(words, phrases, "deactivate", "deactivate", "disable", "turn off");
        family(words, phrases, "suspend", "suspend", "pause", "temporarily stop");
        family(words, phrases, "resume", "resume", "reactivate", "continue service");
        family(words, phrases, "progress", "status", "progress", "track status", "check status");

        family(words, phrases, "invite", "invite", "invitation", "invited", "inviting", "send an invite", "send invitation");
        family(words, phrases, "teamaccess", "team access", "team member", "staff member", "internal user", "employee access",
                "invite staff", "invite employee");
        family(words, phrases, "notification", "notification", "alert", "reminder", "system message", "in app message");
        family(words, phrases, "notificationpreference", "notification preference", "notification settings", "communication preference",
                "message preference", "email preference", "sms preference", "whatsapp preference");

        family(words, phrases, "payment", "payment", "pay", "paid", "paying", "make a payment", "settle payment");
        family(words, phrases, "checkout", "checkout", "check out", "complete purchase");
        family(words, phrases, "invoice", "invoice", "bill", "billing statement", "amount due");
        family(words, phrases, "receipt", "receipt", "proof of payment", "payment confirmation");
        family(words, phrases, "latepayment", "late payment", "overdue payment", "past due", "payment arrears", "unpaid balance");
        family(words, phrases, "refund", "refund", "refunded", "money back", "return payment");
        family(words, phrases, "reversal", "reversal", "reversed payment", "payment reversed", "charge reversed");
        family(words, phrases, "receivingaccount", "receiving account", "payment account", "collection account", "settlement account",
                "bank account for payments");
        family(words, phrases, "subscription", "subscription", "membership", "pricing plan", "service plan", "renewal");

        family(words, phrases, "tenant", "tenant", "renter", "lessee");
        family(words, phrases, "landlord", "landlord", "lessor", "rental owner");
        family(words, phrases, "homeowner", "homeowner", "home owner", "unit owner", "resident owner");
        family(words, phrases, "buyer", "buyer", "purchaser", "home buyer", "property buyer");
        family(words, phrases, "seller", "seller", "property seller");
        family(words, phrases, "rental", "rental", "renting", "tenancy", "lease", "leasing");
        family(words, phrases, "propertysale", "property sale", "sale unit", "unit for sale", "buying property", "sales journey");
        family(words, phrases, "estatemanagement", "estate management", "homeowner management", "community management");
        family(words, phrases, "myunits", "my units", "my unit", "my property", "my properties", "my home", "my homes");
        family(words, phrases, "agreement", "agreement", "contract", "lease agreement", "homeowner agreement", "sale agreement");
        family(words, phrases, "termination", "termination", "terminate", "end tenancy", "end lease", "cancel agreement", "notice to vacate");
        family(words, phrases, "letterofoffer", "letter of offer", "offer letter", "purchase offer", "sales offer");

        family(words, phrases, "marketplace", "marketplace", "soko", "grocery shop", "online shop", "shop groceries");
        family(words, phrases, "merchant", "merchant", "shop owner", "store owner", "grocery seller");
        family(words, phrases, "order", "order", "purchase order", "grocery order", "customer order");
        family(words, phrases, "delivery", "delivery", "dispatch", "shipment", "drop off");
        family(words, phrases, "rider", "rider", "courier", "delivery person", "delivery driver");
        family(words, phrases, "deliverycode", "delivery code", "delivery pin", "handover code", "proof of delivery code");
        family(words, phrases, "serviceprovider", "service provider", "professional", "contractor", "service professional");

        family(words, phrases, "insurance", "insurance", "cover", "insurance cover", "policy cover");
        family(words, phrases, "quotation", "quotation", "quote", "insurance quote", "premium quote");
        family(words, phrases, "insuranceclaim", "insurance claim", "claim", "make a claim", "file a claim");
        family(words, phrases, "marinecargo", "marine cargo", "cargo insurance", "shipping insurance", "import insurance");
        family(words, phrases, "motorinsurance", "motor insurance", "vehicle insurance", "car insurance", "motor cover");

        family(words, phrases, "report", "report", "reporting", "analytics", "dashboard report");
        family(words, phrases, "affiliate", "affiliate", "referral partner", "referrer", "commission partner");
        family(words, phrases, "document", "document", "attachment", "file", "upload document");

        WORDS = Map.copyOf(words);
        PHRASES = phrases.stream()
                .sorted(Comparator.comparingInt((Phrase p) -> p.variant().length()).reversed())
                .toList();
    }

    static String normalizePhrases(String input) {
        String normalized = Objects.toString(input, "").toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'').replace("'", "")
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
        for (Phrase phrase : PHRASES) {
            normalized = phrase.pattern().matcher(normalized).replaceAll(phrase.canonical());
        }
        return normalized;
    }

    static String canonicalWord(String word) {
        String value = word.endsWith("ies") && word.length() > 4 ? word.substring(0, word.length() - 3) + "y"
                : word.endsWith("s") && word.length() > 4 && !word.endsWith("ss") ? word.substring(0, word.length() - 1) : word;
        return WORDS.getOrDefault(value, value);
    }

    private static void family(Map<String, String> words, List<Phrase> phrases, String canonical, String... variants) {
        words.put(canonical, canonical);
        for (String raw : variants) {
            String variant = raw.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", " ").trim();
            if (variant.contains(" ")) {
                Pattern pattern = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(variant) + "(?![a-z0-9])");
                phrases.add(new Phrase(variant, canonical, pattern));
            } else {
                String previous = words.putIfAbsent(variant, canonical);
                if (previous != null && !previous.equals(canonical)) {
                    throw new IllegalStateException("Help vocabulary term belongs to two intents: " + variant);
                }
            }
        }
    }
}
