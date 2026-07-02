using System;
using System.Diagnostics;
using System.Windows.Forms;

internal static class Program
{
    private const string TargetUrl = "https://familyagentai.top";

    [STAThread]
    private static int Main()
    {
        try
        {
            Process.Start(new ProcessStartInfo(TargetUrl)
            {
                UseShellExecute = true
            });
            return 0;
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                "Unable to open FamilyAgent AI website." + Environment.NewLine + Environment.NewLine + ex.Message,
                "FamilyAgent AI",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
            return 1;
        }
    }
}
