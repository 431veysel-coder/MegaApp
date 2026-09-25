#!/usr/bin/env python3
"""
MegaApp Build Monitor - Real-time GitHub Actions monitoring with auto-fix loop
Monitors workflow runs, streams logs, detects errors, suggests/applies fixes
"""

import asyncio
import json
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Any
import logging

# Setup logging
LOG_DIR = Path("/var/minis/workspace/MegaApp/.build-logs")
LOG_DIR.mkdir(parents=True, exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler(LOG_DIR / f"monitor_{datetime.now().strftime('%Y%m%d')}.log"),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

REPO = "431veysel-coder/MegaApp"
WORKFLOW_FILE = "android-build.yml"
PROJECT_DIR = Path("/var/minis/workspace/MegaApp")
POLL_INTERVAL = 15  # seconds


class BuildMonitor:
    def __init__(self):
        self.current_run_id: Optional[int] = None
        self.last_log_line = 0
        self.fix_history: List[Dict] = []
        self.is_running = False
        
    def run_gh(self, args: List[str]) -> Dict:
        """Run gh CLI command and return parsed JSON."""
        cmd = ["gh", "api"] + args
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
            if result.returncode != 0:
                logger.error(f"gh command failed: {' '.join(cmd)}")
                logger.error(f"stderr: {result.stderr}")
                return {}
            return json.loads(result.stdout) if result.stdout.strip() else {}
        except Exception as e:
            logger.error(f"gh command error: {e}")
            return {}

    def get_latest_workflow_run(self) -> Optional[Dict]:
        """Get the latest workflow run for our workflow."""
        data = self.run_gh([
            f"/repos/{REPO}/actions/workflows/{WORKFLOW_FILE}/runs",
            "--jq", ".workflow_runs[0]"
        ])
        return data if data else None

    def get_run_logs(self, run_id: int) -> str:
        """Get logs for a workflow run."""
        # Use gh run view for logs
        cmd = ["gh", "run", "view", str(run_id), "--log"]
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            return result.stdout
        except Exception as e:
            logger.error(f"Failed to get logs: {e}")
            return ""

    def get_job_logs(self, run_id: int, job_name: str) -> str:
        """Get logs for a specific job."""
        cmd = ["gh", "run", "view", str(run_id), "--job", job_name, "--log"]
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            return result.stdout
        except Exception as e:
            logger.error(f"Failed to get job logs: {e}")
            return ""

    def analyze_failure(self, logs: str) -> List[Dict]:
        """Analyze logs for common failure patterns and suggest fixes."""
        fixes = []
        
        # Pattern: Gradle wrapper permission
        if "Permission denied" in logs and "gradlew" in logs:
            fixes.append({
                "type": "gradle_wrapper_permission",
                "description": "gradlew lacks execute permission",
                "fix": "chmod +x ./gradlew",
                "files": ["gradlew"],
                "confidence": 0.95
            })
        
        # Pattern: License not accepted
        if "license" in logs.lower() and ("not accepted" in logs.lower() or "accepted" in logs.lower()):
            fixes.append({
                "type": "android_license",
                "description": "Android SDK licenses not accepted",
                "fix": "yes | sdkmanager --licenses",
                "files": [],
                "confidence": 0.9
            })
        
        # Pattern: NDK version not found
        if "ndk" in logs.lower() and ("not found" in logs.lower() or "could not find" in logs.lower()):
            fixes.append({
                "type": "ndk_version",
                "description": "NDK version mismatch",
                "fix": "Update workflow NDK version or install correct version",
                "files": [".github/workflows/android-build.yml"],
                "confidence": 0.8
            })
        
        # Pattern: Compose compiler version mismatch
        if "compose" in logs.lower() and ("version" in logs.lower() or "mismatch" in logs.lower()):
            fixes.append({
                "type": "compose_version",
                "description": "Kotlin Compose compiler version mismatch",
                "fix": "Align kotlinCompilerExtensionVersion with Kotlin version",
                "files": ["app/build.gradle.kts"],
                "confidence": 0.85
            })
        
        # Pattern: Out of memory
        if "outofmemory" in logs.lower() or "heap space" in logs.lower():
            fixes.append({
                "type": "oom",
                "description": "Gradle/JVM out of memory",
                "fix": "Increase GRADLE_OPTS -Xmx",
                "files": [".github/workflows/android-build.yml", "gradle.properties"],
                "confidence": 0.9
            })
        
        # Pattern: Keystore not found (release build)
        if "keystore" in logs.lower() and ("not found" in logs.lower() or "does not exist" in logs.lower()):
            fixes.append({
                "type": "keystore_missing",
                "description": "Release keystore not configured in secrets",
                "fix": "Add KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD to GitHub Secrets",
                "files": [],
                "confidence": 0.95
            })
        
        # Pattern: Dependency resolution failed
        if "could not resolve" in logs.lower() or "dependency" in logs.lower() and "failed" in logs.lower():
            fixes.append({
                "type": "dependency_resolution",
                "description": "Maven/Gradle dependency resolution failed",
                "fix": "Check repository URLs, add mavenCentral(), check version numbers",
                "files": ["build.gradle.kts", "app/build.gradle.kts", "settings.gradle.kts"],
                "confidence": 0.7
            })
        
        return fixes

    def apply_fix(self, fix: Dict) -> bool:
        """Apply a suggested fix."""
        logger.info(f"Applying fix: {fix['type']} - {fix['description']}")
        
        try:
            if fix["type"] == "gradle_wrapper_permission":
                os.chmod(PROJECT_DIR / "gradlew", 0o755)
                return True
            
            elif fix["type"] == "android_license":
                # Can't run sdkmanager in this environment, but we can update workflow
                workflow_path = PROJECT_DIR / ".github/workflows/android-build.yml"
                content = workflow_path.read_text()
                if "sdkmanager --licenses" not in content:
                    # Add license acceptance step
                    content = content.replace(
                        "- name: Setup Android SDK",
                        "- name: Accept Android Licenses\n        run: yes | sdkmanager --licenses\n      - name: Setup Android SDK"
                    )
                    workflow_path.write_text(content)
                return True
            
            elif fix["type"] == "oom":
                # Increase memory in workflow
                workflow_path = PROJECT_DIR / ".github/workflows/android-build.yml"
                content = workflow_path.read_text()
                if "-Xmx4g" in content:
                    content = content.replace("-Xmx4g", "-Xmx6g")
                    workflow_path.write_text(content)
                # Also update gradle.properties
                gradle_props = PROJECT_DIR / "gradle.properties"
                props_content = gradle_props.read_text()
                if "org.gradle.jvmargs=-Xmx4g" in props_content:
                    props_content = props_content.replace("-Xmx4g", "-Xmx6g")
                    gradle_props.write_text(props_content)
                return True
            
            elif fix["type"] == "compose_version":
                # This needs manual alignment, just log
                logger.warning("Compose version fix needs manual alignment of Kotlin/Compose versions")
                return False
            
            elif fix["type"] == "ndk_version":
                logger.warning("NDK version fix needs workflow update - manual intervention needed")
                return False
            
            elif fix["type"] == "keystore_missing":
                logger.warning("Keystore missing - requires GitHub Secrets configuration (manual)")
                return False
            
            return False
            
        except Exception as e:
            logger.error(f"Fix application failed: {e}")
            return False

    def commit_and_push(self, message: str) -> bool:
        """Commit changes and push to GitHub."""
        try:
            subprocess.run(["git", "add", "-A"], cwd=PROJECT_DIR, check=True)
            subprocess.run(["git", "commit", "-m", message], cwd=PROJECT_DIR, check=True)
            subprocess.run(["git", "push", "origin", "main"], cwd=PROJECT_DIR, check=True)
            logger.info(f"Committed and pushed: {message}")
            return True
        except subprocess.CalledProcessError as e:
            logger.error(f"Git push failed: {e}")
            return False

    def re_run_workflow(self, run_id: int) -> bool:
        """Re-run a failed workflow."""
        cmd = ["gh", "run", "rerun", str(run_id)]
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
            if result.returncode == 0:
                logger.info(f"Re-triggered workflow run {run_id}")
                return True
            else:
                logger.error(f"Re-run failed: {result.stderr}")
                return False
        except Exception as e:
            logger.error(f"Re-run error: {e}")
            return False

    async def monitor_loop(self):
        """Main monitoring loop."""
        self.is_running = True
        logger.info("=" * 60)
        logger.info("BUILD MONITOR STARTED")
        logger.info(f"Repo: {REPO}")
        logger.info(f"Workflow: {WORKFLOW_FILE}")
        logger.info(f"Poll interval: {POLL_INTERVAL}s")
        logger.info("=" * 60)
        
        while self.is_running:
            try:
                run = self.get_latest_workflow_run()
                if not run:
                    logger.warning("No workflow runs found")
                    await asyncio.sleep(POLL_INTERVAL)
                    continue
                
                run_id = run.get("id")
                status = run.get("status")  # queued, in_progress, completed
                conclusion = run.get("conclusion")  # success, failure, cancelled, skipped
                
                # New run detected
                if self.current_run_id != run_id:
                    if self.current_run_id is not None:
                        logger.info(f"New run detected: {run_id} (was {self.current_run_id})")
                    self.current_run_id = run_id
                    self.last_log_line = 0
                
                logger.info(f"Run #{run_id} | Status: {status} | Conclusion: {conclusion or 'pending'}")
                
                if status == "completed":
                    if conclusion == "success":
                        logger.info("✅ BUILD SUCCESS!")
                        self.save_build_report(run, "success")
                        # Wait for new run
                        await asyncio.sleep(POLL_INTERVAL * 4)
                        continue
                    elif conclusion == "failure":
                        logger.error("❌ BUILD FAILED - Analyzing...")
                        await self.handle_failure(run)
                    else:
                        logger.warning(f"Build ended with: {conclusion}")
                
                elif status == "in_progress":
                    # Stream new logs
                    await self.stream_logs(run_id)
                
            except Exception as e:
                logger.error(f"Monitor loop error: {e}")
            
            await asyncio.sleep(POLL_INTERVAL)

    async def stream_logs(self, run_id: int):
        """Stream new log lines from the running workflow."""
        logs = self.get_run_logs(run_id)
        lines = logs.split('\n')
        
        # Only show new lines
        new_lines = lines[self.last_log_line:]
        for line in new_lines:
            if line.strip():
                logger.info(f"[GH] {line}")
        
        self.last_log_line = len(lines)

    async def handle_failure(self, run: Dict):
        """Handle build failure - analyze, fix, re-run."""
        run_id = run.get("id")
        logger.error(f"Handling failure for run #{run_id}")
        
        # Get full logs
        logs = self.get_run_logs(run_id)
        
        # Save failure log
        log_file = LOG_DIR / f"failure_{run_id}_{datetime.now().strftime('%H%M%S')}.log"
        log_file.write_text(logs)
        logger.info(f"Failure log saved: {log_file}")
        
        # Analyze
        fixes = self.analyze_failure(logs)
        
        if not fixes:
            logger.warning("No automatic fixes identified - manual intervention needed")
            self.save_build_report(run, "failure", fixes=[])
            return
        
        logger.info(f"Found {len(fixes)} potential fix(es):")
        for fix in fixes:
            logger.info(f"  - {fix['type']}: {fix['description']} (confidence: {fix['confidence']})")
        
        # Apply fixes with high confidence
        applied = []
        for fix in fixes:
            if fix["confidence"] >= 0.85:
                if self.apply_fix(fix):
                    applied.append(fix)
                    self.fix_history.append({
                        "timestamp": datetime.now().isoformat(),
                        "run_id": run_id,
                        "fix": fix
                    })
        
        if applied:
            # Commit and push fixes
            fix_desc = ", ".join([f["type"] for f in applied])
            if self.commit_and_push(f"fix: auto-fix {fix_desc} (run #{run_id})"):
                logger.info("Fixes pushed, re-running workflow...")
                await asyncio.sleep(5)  # Give GitHub time to register push
                self.re_run_workflow(run_id)
            else:
                logger.error("Failed to push fixes")
        else:
            logger.warning("No high-confidence fixes applied - manual intervention needed")
        
        self.save_build_report(run, "failure", fixes=fixes, applied=applied)

    def save_build_report(self, run: Dict, status: str, fixes: List = None, applied: List = None):
        """Save build report for history."""
        report = {
            "run_id": run.get("id"),
            "run_number": run.get("run_number"),
            "status": status,
            "conclusion": run.get("conclusion"),
            "created_at": run.get("created_at"),
            "updated_at": run.get("updated_at"),
            "head_branch": run.get("head_branch"),
            "head_sha": run.get("head_sha"),
            "workflow": WORKFLOW_FILE,
            "fixes_analyzed": fixes or [],
            "fixes_applied": applied or [],
            "fix_history_count": len(self.fix_history)
        }
        
        report_file = LOG_DIR / f"report_{run.get('id')}_{status}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        report_file.write_text(json.dumps(report, indent=2))
        logger.info(f"Build report saved: {report_file}")

    def stop(self):
        self.is_running = False


async def main():
    monitor = BuildMonitor()
    
    # Handle graceful shutdown
    def signal_handler():
        logger.info("Shutdown signal received")
        monitor.stop()
    
    # Run monitor
    try:
        await monitor.monitor_loop()
    except KeyboardInterrupt:
        logger.info("Monitor stopped by user")
    except Exception as e:
        logger.error(f"Monitor crashed: {e}")
        raise


if __name__ == "__main__":
    asyncio.run(main())