using System;
using System.IO;
using System.Drawing;
using System.Windows.Forms;
using System.Diagnostics;
using Microsoft.Win32;

namespace PhoneBridge.Installer
{
    public class SetupForm : Form
    {
        private ProgressBar progressBar;
        private Label statusLabel;
        private Label titleLabel;
        private Label descLabel;
        private Button installButton;
        private Button cancelButton;
        private CheckBox chkDesktopShortcut;
        private CheckBox chkStartMenuShortcut;
        private CheckBox chkLaunchAfter;

        private readonly string installDir;
        private readonly string sourceDir;

        public SetupForm()
        {
            this.Text = "PhoneBridge - Kurulum Sihirbazı";
            this.Size = new Size(520, 380);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox = false;
            this.BackColor = Color.FromArgb(243, 243, 243);
            this.Font = new Font("Segoe UI", 9.5f, FontStyle.Regular);

            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            installDir = Path.Combine(localAppData, "Programs", "PhoneBridge");

            string currentDir = AppDomain.CurrentDomain.BaseDirectory;
            string potentialSource1 = Path.Combine(currentDir, "..", "bin", "Debug", "net11.0-windows10.0.26100.0", "win-x64");
            string potentialSource2 = Path.Combine(currentDir, "Payload");
            string potentialSource3 = currentDir;

            if (Directory.Exists(potentialSource1) && File.Exists(Path.Combine(potentialSource1, "PhoneBridge.exe")))
            {
                sourceDir = Path.GetFullPath(potentialSource1);
            }
            else if (Directory.Exists(potentialSource2) && File.Exists(Path.Combine(potentialSource2, "PhoneBridge.exe")))
            {
                sourceDir = Path.GetFullPath(potentialSource2);
            }
            else
            {
                sourceDir = Path.GetFullPath(potentialSource3);
            }

            InitUi();
        }

        private void InitUi()
        {
            Panel headerPanel = new Panel
            {
                Dock = DockStyle.Top,
                Height = 85,
                BackColor = Color.FromArgb(0, 120, 212)
            };

            titleLabel = new Label
            {
                Text = "PhoneBridge v1.0.0 Kurulumu",
                Font = new Font("Segoe UI", 14f, FontStyle.Bold),
                ForeColor = Color.White,
                Location = new Point(24, 16),
                AutoSize = true
            };

            descLabel = new Label
            {
                Text = "Android ve Windows arasında kusursuz ekosistem bağlantısı.",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Regular),
                ForeColor = Color.FromArgb(230, 240, 255),
                Location = new Point(24, 46),
                AutoSize = true
            };

            headerPanel.Controls.Add(titleLabel);
            headerPanel.Controls.Add(descLabel);
            this.Controls.Add(headerPanel);

            Label pathInfo = new Label
            {
                Text = "Kurulum Konumu:\n" + installDir,
                Location = new Point(24, 105),
                Size = new Size(460, 42),
                ForeColor = Color.FromArgb(60, 60, 60)
            };
            this.Controls.Add(pathInfo);

            chkDesktopShortcut = new CheckBox
            {
                Text = "Masaüstüne kısayol oluştur",
                Checked = true,
                Location = new Point(24, 155),
                AutoSize = true
            };
            this.Controls.Add(chkDesktopShortcut);

            chkStartMenuShortcut = new CheckBox
            {
                Text = "Başlat Menüsüne kısayol ekle",
                Checked = true,
                Location = new Point(24, 182),
                AutoSize = true
            };
            this.Controls.Add(chkStartMenuShortcut);

            chkLaunchAfter = new CheckBox
            {
                Text = "Kurulum tamamlandığında PhoneBridge'i hemen başlat",
                Checked = true,
                Location = new Point(24, 209),
                AutoSize = true
            };
            this.Controls.Add(chkLaunchAfter);

            progressBar = new ProgressBar
            {
                Location = new Point(24, 245),
                Size = new Size(456, 18),
                Visible = false,
                Style = ProgressBarStyle.Continuous
            };
            this.Controls.Add(progressBar);

            statusLabel = new Label
            {
                Text = "Kurulum için 'Kurulumu Başlat' butonuna tıklayın.",
                Location = new Point(24, 270),
                Size = new Size(456, 24),
                ForeColor = Color.FromArgb(90, 90, 90)
            };
            this.Controls.Add(statusLabel);

            installButton = new Button
            {
                Text = "Kurulumu Başlat",
                Location = new Point(240, 298),
                Size = new Size(130, 32),
                BackColor = Color.FromArgb(0, 120, 212),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat
            };
            installButton.FlatAppearance.BorderSize = 0;
            installButton.Click += (s, e) => StartInstall();
            this.Controls.Add(installButton);

            cancelButton = new Button
            {
                Text = "İptal",
                Location = new Point(380, 298),
                Size = new Size(100, 32),
                BackColor = Color.FromArgb(225, 225, 225),
                FlatStyle = FlatStyle.Flat
            };
            cancelButton.FlatAppearance.BorderSize = 0;
            cancelButton.Click += (s, e) => this.Close();
            this.Controls.Add(cancelButton);
        }

        private void StartInstall()
        {
            installButton.Enabled = false;
            cancelButton.Enabled = false;
            progressBar.Visible = true;
            statusLabel.Text = "Dosyalar hazırlanıyor ve kopyalanıyor...";
            progressBar.Value = 10;
            Application.DoEvents();

            try
            {
                foreach (var p in Process.GetProcessesByName("PhoneBridge"))
                {
                    try { p.Kill(); p.WaitForExit(2000); } catch { }
                }

                if (!Directory.Exists(installDir))
                {
                    Directory.CreateDirectory(installDir);
                }

                progressBar.Value = 25;
                statusLabel.Text = "Uygulama ikili dosyaları kopyalanıyor...";
                Application.DoEvents();

                CopyDirectory(sourceDir, installDir);

                progressBar.Value = 70;
                statusLabel.Text = "Kısayollar ve sistem kayıtları oluşturuluyor...";
                Application.DoEvents();

                string exePath = Path.Combine(installDir, "PhoneBridge.exe");

                if (chkDesktopShortcut.Checked)
                {
                    string desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                    CreateShortcut(Path.Combine(desktop, "PhoneBridge.lnk"), exePath, "PhoneBridge - Akıllı Telefon Entegrasyonu");
                }

                if (chkStartMenuShortcut.Checked)
                {
                    string startMenu = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
                    CreateShortcut(Path.Combine(startMenu, "PhoneBridge.lnk"), exePath, "PhoneBridge - Akıllı Telefon Entegrasyonu");
                }

                RegisterUninstall(exePath);

                progressBar.Value = 100;
                statusLabel.Text = "Kurulum başarıyla tamamlandı!";
                statusLabel.ForeColor = Color.FromArgb(16, 124, 65);
                Application.DoEvents();

                if (chkLaunchAfter.Checked && File.Exists(exePath))
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = exePath,
                        WorkingDirectory = installDir,
                        UseShellExecute = true
                    });
                }

                MessageBox.Show(
                    "PhoneBridge başarıyla kuruldu!\n\nArtık masaüstünden veya Başlat menüsünden tek tıkla çalıştırabilirsiniz.",
                    "Kurulum Tamamlandı",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information);

                this.Close();
            }
            catch (Exception ex)
            {
                MessageBox.Show("Kurulum sırasında bir hata oluştu:\n" + ex.Message, "Hata", MessageBoxButtons.OK, MessageBoxIcon.Error);
                installButton.Enabled = true;
                cancelButton.Enabled = true;
            }
        }

        private static void CopyDirectory(string source, string destination)
        {
            foreach (string dir in Directory.GetDirectories(source, "*", SearchOption.AllDirectories))
            {
                string rel = dir.Substring(source.Length).TrimStart('\\', '/');
                string destDir = Path.Combine(destination, rel);
                if (!Directory.Exists(destDir))
                {
                    Directory.CreateDirectory(destDir);
                }
            }

            foreach (string file in Directory.GetFiles(source, "*.*", SearchOption.AllDirectories))
            {
                string rel = file.Substring(source.Length).TrimStart('\\', '/');
                string destFile = Path.Combine(destination, rel);
                File.Copy(file, destFile, true);
            }
        }

        private static void CreateShortcut(string shortcutPath, string targetPath, string description)
        {
            try
            {
                Type shellType = Type.GetTypeFromProgID("WScript.Shell");
                if (shellType != null)
                {
                    dynamic shell = Activator.CreateInstance(shellType);
                    dynamic shortcut = shell.CreateShortcut(shortcutPath);
                    shortcut.TargetPath = targetPath;
                    shortcut.WorkingDirectory = Path.GetDirectoryName(targetPath);
                    shortcut.Description = description;
                    shortcut.IconLocation = targetPath + ",0";
                    shortcut.Save();
                }
            }
            catch { }
        }

        private void RegisterUninstall(string exePath)
        {
            try
            {
                using (var key = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\PhoneBridge"))
                {
                    if (key != null)
                    {
                        key.SetValue("DisplayName", "PhoneBridge");
                        key.SetValue("DisplayVersion", "1.0.0");
                        key.SetValue("Publisher", "Ömer Faruk Karaca");
                        key.SetValue("DisplayIcon", exePath);
                        key.SetValue("InstallLocation", installDir);
                        key.SetValue("UninstallString", "\"" + exePath + "\" --uninstall");
                        key.SetValue("EstimatedSize", 45000);
                        key.SetValue("NoModify", 1);
                        key.SetValue("NoRepair", 1);
                    }
                }
            }
            catch { }
        }

        [STAThread]
        public static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new SetupForm());
        }
    }
}
