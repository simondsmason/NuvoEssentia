#!/usr/bin/env python3
"""
Hubitat Nuvo Essentia Driver Deployment Script (Safari/Selenium Version)
Automates updating the driver code in Hubitat's web editor using system Safari.
Requires: 'Allow Remote Automation' in Safari > Develop menu.
"""

import sys
import os
import time
from pathlib import Path

# --- Configuration ---
# TODO: REPLACE THIS WITH YOUR DRIVER ID (Found in the URL: /driver/editor/XXX)
# Or leave as "REPLACE_WITH_DRIVER_ID" to use auto-discovery
DRIVER_ID = "REPLACE_WITH_DRIVER_ID" 
# Hub IP - can be overridden via command line argument
# Default: HubitatC8Pro-2 (where Nuvo devices are located)
HUB_IP = "192.168.2.19"  # HubitatC8Pro-2
CODE_FILENAME = "NuvoEssentia.groovy"
DRIVER_NAME = "Nuvo Essentia"  # Name to search for in driver list
# ---------------------

try:
    from selenium import webdriver
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC
    from selenium.common.exceptions import TimeoutException, NoSuchElementException
except ImportError:
    print("ERROR: selenium not installed. Installing...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "selenium", "--quiet"])
    from selenium import webdriver
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC
    from selenium.common.exceptions import TimeoutException, NoSuchElementException

def deploy_driver(driver_url, driver_code_path):
    """
    Deploy driver code to Hubitat editor using Selenium/Safari
    """
    driver_code_path = Path(driver_code_path)
    
    if not driver_code_path.exists():
        print(f"ERROR: Driver code file not found: {driver_code_path}")
        return False
    
    # Read the driver code
    with open(driver_code_path, 'r', encoding='utf-8') as f:
        driver_code = f.read()
    
    print(f"Loaded driver code from: {driver_code_path}")
    print(f"Code length: {len(driver_code)} characters")
    
    print("Launching Safari (ensure 'Allow Remote Automation' is on)...")
    try:
        driver = webdriver.Safari()
    except Exception as e:
        print(f"ERROR: Failed to launch Safari. Make sure 'Allow Remote Automation' is enabled in Develop menu.\nDetails: {e}")
        return False
        
    try:
        print(f"Navigating to: {driver_url}")
        driver.get(driver_url)
        
        # Check for login (dumb check for password field)
        try:
            if driver.find_elements(By.CSS_SELECTOR, "input[type='password']"):
                print("⚠️  Login page detected. Please login manually in the browser window.")
                print("Waiting 15 seconds for manual login...")
                time.sleep(15)
        except:
            pass
            
        # Wait for CodeMirror
        print("Waiting for CodeMirror editor to load...")
        try:
            WebDriverWait(driver, 15).until(EC.presence_of_element_located((By.CLASS_NAME, "CodeMirror")))
            print("✓ CodeMirror editor found")
            time.sleep(2) # Extra buffer for initialization
        except TimeoutException:
            print("❌ CodeMirror editor not found.")
            return False

        # Update Code
        print("Updating code...")
        result = driver.execute_script(f"""
            var driverCode = arguments[0];
            var cmElement = document.querySelector('.CodeMirror');
            if (!cmElement) return {{success: false, error: 'Element not found'}};
            
            var cm = cmElement.CodeMirror || (window.CodeMirror && cmElement.cm);
            if (!cm) {{
                 // Try digging deeper
                 var editors = document.querySelectorAll('.CodeMirror');
                 for (var i=0; i<editors.length; i++) {{
                     if (editors[i].CodeMirror) {{ cm = editors[i].CodeMirror; break; }}
                 }}
            }}
            
            if (!cm) return {{success: false, error: 'Instance not found'}};
            
            cm.setValue(driverCode);
            
            // Trigger changes
            if (cm.trigger) cm.trigger('change');
            
            // Focus blur trick
            if (cm.getInputField) {{
                if (document.activeElement !== cm.getInputField()) cm.getInputField().focus();
                cm.getInputField().blur();
            }}
            
            return {{success: true}};
        """, driver_code)
        
        if result and result.get('success'):
            print("✓ Code updated via JS")
        else:
            print(f"⚠️  JS Update failed: {result}")
            return False
            
        # Click Save
        print("Looking for Save button...")
        try:
            # Hubitat save buttons can vary, try generic matching
            save_btn = None
            selectors = [
                 # Use dot (.) to match text in descendants (fixes PrimeVue <button><span>Save</span></button>)
                "//button[contains(translate(., 'SAVE', 'save'), 'save')]", 
                "//button[contains(@class, 'bg-hubitat-primary-green')]", # Specific to Hubitat's primary button
                "//button[contains(@class, 'btn-primary')]",
                "//button[@type='submit']",
                "//input[@value='Save']"
            ]
            
            for xpath in selectors:
                try:
                    btns = driver.find_elements(By.XPATH, xpath)
                    for btn in btns:
                        if btn.is_displayed() and btn.is_enabled():
                            # Double check it's not a "Delete" or "Cancel" button masquerading
                            txt = btn.text.lower()
                            if "delete" in txt or "cancel" in txt:
                                continue
                            save_btn = btn
                            print(f"✓ Found Save button using: {xpath}")
                            break
                    if save_btn: break
                except: pass
            
            if save_btn:
                # Scroll to it
                driver.execute_script("arguments[0].scrollIntoView(true);", save_btn)
                time.sleep(0.5)
                print("Clicking Save button...")
                try:
                    save_btn.click()
                except:
                    # Fallback to JS click
                    driver.execute_script("arguments[0].click();", save_btn)
                
                time.sleep(3) # Wait for save
            else:
                print("⚠️  Save button not found. Dumping visible buttons for debug:")
                buttons = driver.find_elements(By.TAG_NAME, "button")
                for b in buttons:
                    if b.is_displayed():
                        print(f"  - Button: text='{b.text}', class='{b.get_attribute('class')}', type='{b.get_attribute('type')}'")
                inputs = driver.find_elements(By.TAG_NAME, "input")
                for i in inputs:
                     if i.is_displayed() and i.get_attribute('type') in ['submit', 'button']:
                        print(f"  - Input: value='{i.get_attribute('value')}', class='{i.get_attribute('class')}'")

                return False
                
        except Exception as e:
            print(f"Error finding/clicking save: {e}")
            return False
            
        # Check for errors
        print("Checking for compilation errors...")
        errors_found = []
        
        # 1. Yellow Banner
        try:
            banners = driver.find_elements(By.CSS_SELECTOR, ".alert-warning")
            for b in banners:
                if b.is_displayed():
                    errors_found.append(f"Banner: {b.text}")
        except: pass
        
        # 2. Body Text Scan
        try:
            body_text = driver.find_element(By.TAG_NAME, "body").text.lower()
            keywords = ['expecting', 'syntax error', 'unexpected token', 'compilation error']
            for k in keywords:
                if k in body_text and f"{k} @" in body_text: # rudimetary check
                    # extract line
                    lines = body_text.split('\n')
                    for line in lines:
                        if k in line:
                            errors_found.append(line.strip())
        except: pass
        
        if errors_found:
            print("\n❌ COMPILATION ERRORS FOUND:")
            for e in errors_found:
                print(f"  - {e}")
            return False
        else:
            print("✅ No compilation errors detected. Code saved successfully!")
            return True

    except Exception as e:
        print(f"Global Error: {e}")
        return False
    finally:
        driver.quit()

if __name__ == "__main__":
    driver_id = DRIVER_ID
    hub_ip = HUB_IP
    
    # Parse command line arguments
    # Usage: python3 deploy_driver.py [driver_id] [hub_ip]
    if len(sys.argv) > 1:
        if sys.argv[1].isdigit():
            driver_id = sys.argv[1]
        elif "." in sys.argv[1]:  # Looks like an IP address
            hub_ip = sys.argv[1]
    
    if len(sys.argv) > 2:
        if sys.argv[2].isdigit():
            driver_id = sys.argv[2]
        elif "." in sys.argv[2]:  # Looks like an IP address
            hub_ip = sys.argv[2]
    
    # Update HUB_IP for auto-discovery if it was changed
    if hub_ip != HUB_IP:
        HUB_IP = hub_ip
        print(f"Using hub IP: {HUB_IP}")
    
    # Auto-Discovery
    if driver_id == "REPLACE_WITH_DRIVER_ID":
        print(f"Driver ID missing. Searching for '{DRIVER_NAME}' in Drivers Code (Safari)...")
        try:
            driver = webdriver.Safari()
            # Try driver/list endpoint (patterned after /app/list for apps)
            list_url = f"http://{HUB_IP}/driver/list"
            print(f"Navigating to {list_url}")
            driver.get(list_url)
            
            # Wait for page to load
            time.sleep(2)
            
            # Wait for list - try multiple possible selectors
            link = None
            try:
                # Try finding by partial link text
                WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.PARTIAL_LINK_TEXT, DRIVER_NAME)))
                link = driver.find_element(By.PARTIAL_LINK_TEXT, DRIVER_NAME)
            except TimeoutException:
                # Try finding in any link that contains the driver name
                try:
                    links = driver.find_elements(By.TAG_NAME, "a")
                    for l in links:
                        if DRIVER_NAME.lower() in l.text.lower():
                            link = l
                            break
                except:
                    pass
            
            if link:
                href = link.get_attribute("href")
                if href and "editor/" in href:
                    driver_id = href.split("editor/")[1].split("/")[0].split("?")[0]  # Handle query params
                    print(f"✓ Found Driver ID: {driver_id}")
                else:
                    print(f"⚠️  Found link but href format unexpected: {href}")
            else:
                print(f"❌ Could not find '{DRIVER_NAME}' link in the driver list.")
                print(f"   Tried URL: {list_url}")
                print("   Please check:")
                print("   1. The driver list endpoint may be different")
                print("   2. The driver name may be different")
                print("   3. You may need to manually set DRIVER_ID in the script")
            
            driver.quit()
        except Exception as e:
            print(f"Discovery Error: {e}")
            import traceback
            traceback.print_exc()
            
    if driver_id == "REPLACE_WITH_DRIVER_ID":
        print("❌ Failed to find Driver ID. Please set it manually.")
        print(f"   You can:")
        print(f"   1. Set DRIVER_ID in the script")
        print(f"   2. Pass it as argument: python3 deploy_driver.py <driver_id>")
        print(f"   3. Pass it with hub IP: python3 deploy_driver.py <hub_ip> <driver_id>")
        sys.exit(1)
        
    driver_url = f"http://{hub_ip}/driver/editor/{driver_id}"
    driver_code_path = os.path.join(os.path.dirname(__file__), CODE_FILENAME)
    
    print(f"\nDeploying to: {driver_url}")
    print(f"Code file: {driver_code_path}\n")
    
    deploy_driver(driver_url, driver_code_path)
