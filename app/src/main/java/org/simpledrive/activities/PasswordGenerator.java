package org.simpledrive.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import org.simpledrive.R;

import java.util.Random;

public class PasswordGenerator extends AppCompatActivity implements TextWatcher {
    // Interface
    private TextView txtPassword;
    private TextView passwordStrength;
    private EditText txtLength;
    private CheckBox useLowercase;
    private CheckBox useUppercase;
    private CheckBox useNumbers;
    private CheckBox useSpecials;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_passwordgenerator);

        txtPassword = (TextView) findViewById(R.id.password);
        txtPassword.addTextChangedListener(this);
        txtLength = (EditText) findViewById(R.id.length);
        passwordStrength = (TextView) findViewById(R.id.passwordStrength);
        useLowercase = (CheckBox) findViewById(R.id.use_lowercase);
        useUppercase = (CheckBox) findViewById(R.id.use_uppercase);
        useNumbers = (CheckBox) findViewById(R.id.use_numbers);
        useSpecials = (CheckBox) findViewById(R.id.use_specials);

        initToolbar();
    }

    public void onBackPressed() {
        Intent returnIntent = new Intent();
        setResult(RESULT_CANCELED, returnIntent);
        finish();
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.generate:
                generatePassword();
                break;

            case R.id.ok:
                returnPassword();
                break;
        }
    }

    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationIcon(R.drawable.ic_arrow);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    private void generatePassword() {
        try {
            int length = Integer.parseInt(txtLength.getText().toString());
            final Random random = new Random();
            String alphabet = initAlphabet(useLowercase.isChecked(), useUppercase.isChecked(), useNumbers.isChecked(), useSpecials.isChecked());
            StringBuilder tmpPassword = new StringBuilder();

            if (length > 0 && alphabet.length() > 0) {
                for (int i = 0; i < length; i++) {
                    tmpPassword.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
            }

            txtPassword.setText(tmpPassword.toString());
        } catch (NumberFormatException e) {
            // Something
        }
    }

    private String initAlphabet(boolean lowercase, boolean uppercase, boolean numbers, boolean specials) {
        StringBuilder tmp = new StringBuilder();
        String specialCharacters = "!§$%&/()=?.-;:_";

        if (lowercase) {
            for (char ch = 'a'; ch <= 'z'; ch++) {
                tmp.append(ch);
            }
        }
        if (uppercase) {
            for (char ch = 'A'; ch <= 'Z'; ch++) {
                tmp.append(ch);
            }
        }
        if (numbers) {
            for (char ch = '0'; ch <= '9'; ch++) {
                tmp.append(ch);
            }
        }
        if (specials) {
            tmp.append(specialCharacters);
        }
        return tmp.toString();
    }

    private void returnPassword() {
        String passwordText = txtPassword.getText().toString().replaceAll("\\s++$", "");
        Intent i = new Intent();
        i.putExtra("password", passwordText);
        setResult(RESULT_OK, i);
        finish();
    }

    private int checkPasswordStrength(String pw) {
        int score = 0;

        // Longer than 6 chars
        if (pw.length() > 6) score++;

        // Longer than 12 chars
        if (pw.length() > 12) score++;

        // Contains digit
        if (pw.matches(".*[0-9]*.")) {
            score++;
        }

        // Contains lowercase and uppercase
        if (pw.matches(".*[a-z].*") && pw.matches(".*[A-Z].*")) {
            score++;
        }

        // Contains special char
        if (pw.matches(".*[ /:-@\\[\\-'{-~].*")) {
            score++;
        }

        // Password with 6 or less characters is always a bad idea
        score = (pw.length() <= 6) ? (Math.min(score, 1)) : score;
        return score;
    }

    private void showPasswordStrength(String pw) {
        String[] strengths = {"Very weak", "Weak", "Ok", "Good", "Strong", "Very strong"};
        int score = checkPasswordStrength(pw);

        String status = strengths[score];
        passwordStrength.setVisibility(View.VISIBLE);
        passwordStrength.setText(status);
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        String password = txtPassword.getText().toString().replaceAll("\\s++$", "");
        if (password.length() > 0) {
            showPasswordStrength(password);
        }
        else {
            passwordStrength.setVisibility(View.GONE);
        }
    }
}